package co.rsk.storagerent;

import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Repository;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.program.Program;

import java.util.*;

public class StorageRentManager {

    // rentedNodes are value-containing nodes (account state or storage cells nodes)
    // that are involved in a transaction execution.
    private final Map<ByteArrayWrapper, RentedNode> rentedNodes; // TODO(fedejinich) analyse collection overflows

    // avoid duplicates by using a Set
    private final Set<ByteArrayWrapper> mismatches = new HashSet<>(); // TODO(fedejinich) refactor this, create a default constructor

    public StorageRentManager(Map<ByteArrayWrapper, RentedNode> rentedNodes) {
        this.rentedNodes = rentedNodes;
    }

    public void trackNode(RentedNode node) {
        ByteArrayWrapper key = node.getKey();

        if(this.rentedNodes.containsKey(key)) {
            RentedNode containedNode = this.rentedNodes.get(key);
            if(containedNode.shouldBeReplaced(node)) {
                // it needs to be replaced because it has the lowest threshold
                this.rentedNodes.put(key, node);
            }
        } else {
            this.rentedNodes.put(key, node);
        }
    }

    @VisibleForTesting
    protected Map<ByteArrayWrapper, RentedNode> getRentedNodes() {
        return this.rentedNodes;
    }

    /**
     * Pays storage rent (RSKIP240)
     *
     * @param gas initial gas amount
     * @param repository a repository to fetch the relevant data and to update the storage rent timestamps
     * @return gas leftover
     * */
    public long payStorageRent(long gas, long currentBlockTimestamp, Repository repository) {
        Collection<RentedNode> rentedNodes = this.rentedNodes.values();

        // fetch relevant data to pay storage rent
        rentedNodes.forEach(node -> {
            Map<String, Long> storageRentData = repository.fetchStorageRentData(node.getKey().getData());

            node.setStorageRentData(storageRentData.get("nodeSize"), storageRentData.get("lastRentPaidTimestamp"));
//            node.setNodeSize(storageRentData.get("nodeSize"));
//            node.setLastRentPaidTimestamp(storageRentData.get("lastRentPaidTimestamp"));
        });

        long cumulativeRent = rentedNodes.stream()
                .map(v -> v.payableRent(currentBlockTimestamp))
                .reduce(GasCost::add)
                .get(); // todo(fedejinich) this should always be present, should I throw an exception?

        if(gas < cumulativeRent) {
            // todo(fedejinich) this is not the right way to throw an OOG
            throw new Program.OutOfGasException("not enough gas to pay storage rent. gas: {}, gasNeeded: {}");
        }

        rentedNodes.forEach(node -> repository.updateLastRentPaidTimestamp(
                node.getKey().getData(),
                node.updatedRentTimestamp(currentBlockTimestamp)));

        return GasCost.subtract(gas, cumulativeRent);
    }

    public void trackMismatch(byte[] key) {
        this.mismatches.add(new ByteArrayWrapper(key));
    }

    public Set<ByteArrayWrapper> getMismatches() {
        return mismatches;
    }
}
