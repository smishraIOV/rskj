package co.rsk.storagerent;

import org.ethereum.core.Repository;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.program.Program;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;

/**
 * Stateless class, useful for the StorageRent feature (RSKIP240)
 * https://github.com/rsksmart/RSKIPs/blob/master/IPs/RSKIP240.md
 * */
public class StorageRentManager {
    public static final long NO_RENT_TO_PAY = -1;

    private long paidRent = NO_RENT_TO_PAY;
    private final Collection<RentedNode> rentedNodes;

    public StorageRentManager(Collection<RentedNode> storageRentNodes) {
        this.rentedNodes = storageRentNodes;
    }

    /**
     * Pay storage rent
     *
     * @param gas initial gas amount
     * @param executionBlockTimestamp execution block timestamp
     * @param repository repository to fetch the relevant data and to update the rent timestamps
     * @return gas leftover
     * */
    public long pay(long gas, long executionBlockTimestamp, Repository repository) {
        // fetch relevant data to pay storage rent
        rentedNodes.forEach(node -> {
            Map<String, Long> storageRentData = repository.fetchStorageRentData(node.getKey().getData());

            node.setStorageRentData(storageRentData.get("nodeSize"), storageRentData.get("lastRentPaidTimestamp"));
        });

        // todo(fedejinich) should i add a check for rented nodes > 0?

        long cumulativeRent = rentedNodes.stream()
                .map(v -> v.payableRent(executionBlockTimestamp))
                .reduce(GasCost::add)
                .get(); // todo(fedejinich) this should always be present, should I throw an exception?

        if(gas < cumulativeRent) {
            // todo(fedejinich) this is not the right way to throw an OOG
            throw new Program.OutOfGasException("not enough gas to pay storage rent. gas: {}, gasNeeded: {}");
        }

        rentedNodes.forEach(node -> repository.updateLastRentPaidTimestamp(
                node.getKey().getData(),
                node.updatedRentTimestamp(executionBlockTimestamp)));

        this.paidRent = cumulativeRent;

        return GasCost.subtract(gas, cumulativeRent);
    }

    public long getPaidRent() {
        return paidRent;
    }

    public Collection<RentedNode> getRentedNodes() {
        return rentedNodes;
    }

    /**
     * Commits nodes from another storage rent manager, which is used to track nodes from internal transactions
     * @param temporaryStorageRentManager a temporary rent manager, only used to track nodes
     * */
    public void commit(StorageRentManager temporaryStorageRentManager) {
        temporaryStorageRentManager.rentedNodes.values().forEach(node -> trackNode(node));
    }
}
