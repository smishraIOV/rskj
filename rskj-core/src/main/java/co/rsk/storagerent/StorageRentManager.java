package co.rsk.storagerent;

import org.ethereum.core.Repository;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.program.Program;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Stateless class, useful for the StorageRent feature (RSKIP240)
 * https://github.com/rsksmart/RSKIPs/blob/master/IPs/RSKIP240.md
 * */
public class StorageRentManager {
    public static final long NO_RENT_TO_PAY = -1;
    public static final long NO_ROLLBACK_YET = -1;

    private final Collection<RentedNode> rentedNodes;
    private final List<RentedNode> rolledBackNodes;

    private long paidRent = NO_RENT_TO_PAY;
    private long rollbacksFees = NO_ROLLBACK_YET;

    public StorageRentManager(Collection<RentedNode> storageRentNodes, List<RentedNode> rolledBackNodes) {
        this.rentedNodes = storageRentNodes;
        this.rolledBackNodes = rolledBackNodes;
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
        // todo(fedejinich) duplicated code
        // fetch relevant data to pay fees for rollbacks
        rolledBackNodes.forEach(node -> {
            Map<String, Long> storageRentData = repository.fetchStorageRentData(node.getKey().getData());

            node.setStorageRentData(storageRentData.get("nodeSize"), storageRentData.get("lastRentPaidTimestamp"));
        });

        // todo(fedejinich) should i add a check for rented nodes > 0?

        long cumulativeRent = rentedNodes.stream()
                .map(v -> v.payableRent(executionBlockTimestamp))
                .reduce(GasCost::add)
                .get(); // todo(fedejinich) this should always be present, should I throw an exception?

        long rollbacksFees = rolledBackNodes.isEmpty() ? 0 : rolledBackNodes.stream()
                .map(v -> v.rollbackFee(executionBlockTimestamp))
                .reduce(GasCost::add)
                .get();

        long totalRent = cumulativeRent + rollbacksFees;

        if(gas < totalRent) {
            // todo(fedejinich) this is not the right way to throw an OOG
            throw new Program.OutOfGasException("not enough gas to pay storage rent. gas: {}, gasNeeded: {}");
        }

        rentedNodes.forEach(node -> repository.updateLastRentPaidTimestamp(
                node.getKey().getData(),
                node.updatedRentTimestamp(executionBlockTimestamp)));

        this.paidRent = totalRent;
        this.rollbacksFees = rollbacksFees;

        return GasCost.subtract(gas, this.paidRent);
    }

    public long getPaidRent() {
        return paidRent;
    }

    public long getRollbacksFees() {
        return this.rollbacksFees;
    }

    public Collection<RentedNode> getRentedNodes() {
        return rentedNodes;
    }
}
