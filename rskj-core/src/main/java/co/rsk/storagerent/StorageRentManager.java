package co.rsk.storagerent;

import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Repository;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.program.Program;

import java.util.*;

public class StorageRentManager {
    private final Map<byte[], ValueContainingNode> valueContainingNodes;
    // avoid duplicates by using a Set
    private final Set<byte[]> mismatches = new HashSet<>(); // TODO(fedejinich) refactor this, create a default constructor

    public StorageRentManager(Map<byte[], ValueContainingNode> valueContainingNodes) {
        this.valueContainingNodes = valueContainingNodes;
    }

    public void trackValueContainingNode(ValueContainingNode valueContainingNode) {
        byte[] key = valueContainingNode.getKey().encode();
        this.valueContainingNodes.put(key, valueContainingNode); // todo(fedejinich) any priority between READ/WRITE operations
    }

    @VisibleForTesting
    protected Map<byte[], ValueContainingNode> getValueContainingNode() {
        return this.valueContainingNodes;
    }

    /**
     * Pays storage rent (RSKIP240)
     *
     * @param gas initial gas amount
     * @param repository a repository to fetch the relevant data and to update the storage rent timestamps
     * @return gas leftover
     * */
    public long payStorageRent(long gas, long currentBlockTimestamp, Repository repository) {
        fetchStorageRentData(repository); // fetch relevant data to pay storage rent

        long cumulativeRent = cumulativeRent(currentBlockTimestamp);

        if(gas < cumulativeRent) {
            // todo(fedejinich) this is not the right way to throw an OOG
            throw new Program.OutOfGasException("not enough gas to pay storage rent. gas: {}, gasNeeded: {}");
        }

        valueContainingNodes.values().forEach(node -> repository.updateLastRentPaidTimestamp(
                node.getKey().encode(),
                node.updatedRentTimestamp(currentBlockTimestamp)));

        return GasCost.subtract(gas, cumulativeRent);
    }

    private long cumulativeRent(long currentBlockTimestamp) {
        return valueContainingNodes
                .values()
                .stream()
                .map(v -> v.payableRent(currentBlockTimestamp))
                .reduce(GasCost::add)
                .get(); // todo(fedejinich) this should always be present, should I throw an exception?
    }

    private void fetchStorageRentData(Repository repository) {
        this.valueContainingNodes.values().forEach(valueContainingNode -> {
            Map<String, Long> storageRentData = repository.fetchStorageRentData(valueContainingNode.getKey().encode());

            valueContainingNode.setNodeSize(storageRentData.get("nodeSize"));
            valueContainingNode.setLastRentPaidTimestamp(storageRentData.get("lastRentPaidTimestamp"));
        });
    }

    public void trackMismatch(byte[] key) {
        this.mismatches.add(key);
    }
}
