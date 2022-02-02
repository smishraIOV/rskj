package co.rsk.storagerent;

import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Repository;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.program.Program;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class StorageRentManager {
    // TODO(fedejinich) analyse collection overflows
    private final Map<ByteArrayWrapper, ValueContainingNode> valueContainingNodes;
    // avoid duplicates by using a Set
    private final Set<ByteArrayWrapper> mismatches = new HashSet<>(); // TODO(fedejinich) refactor this, create a default constructor

    public StorageRentManager(Map<ByteArrayWrapper, ValueContainingNode> valueContainingNodes) {
        this.valueContainingNodes = valueContainingNodes;
    }

    public void trackValueContainingNode(ValueContainingNode nodeToTrack) {
        ByteArrayWrapper key = nodeToTrack.getKey();

        if(this.valueContainingNodes.containsKey(key)) {
            ValueContainingNode containedNode = this.valueContainingNodes.get(key);
            if(containedNode.shouldBeReplaced(nodeToTrack)) {
                // it needs to be replaced because it has the lowest threshold
                this.valueContainingNodes.put(key, nodeToTrack);
            }
        } else {
            this.valueContainingNodes.put(key, nodeToTrack);
        }
    }

    @VisibleForTesting
    protected Map<ByteArrayWrapper, ValueContainingNode> getValueContainingNode() {
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
                node.getKey().getData(),
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
            Map<String, Long> storageRentData = repository.fetchStorageRentData(valueContainingNode.getKey().getData());

            valueContainingNode.setNodeSize(storageRentData.get("nodeSize"));
            valueContainingNode.setLastRentPaidTimestamp(storageRentData.get("lastRentPaidTimestamp"));
        });
    }

    public void trackMismatch(byte[] key) {
        this.mismatches.add(new ByteArrayWrapper(key));
    }

    public Set<ByteArrayWrapper> getMismatches() {
        return mismatches;
    }
}
