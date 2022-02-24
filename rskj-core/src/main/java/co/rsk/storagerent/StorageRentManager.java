package co.rsk.storagerent;

import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Repository;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.program.Program;

import java.util.*;
import java.util.function.Function;

/**
 * StorageRentManager, responsible for paying StorageRent (RSKIP240)
 * https://github.com/rsksmart/RSKIPs/blob/master/IPs/RSKIP240.md
 * */
public class StorageRentManager {
    public static final long NO_RENT_TO_PAY = -1;
    public static final long NO_ROLLBACK_YET = -1;

    private final Collection<RentedNode> rentedNodes;
    private final List<RentedNode> rolledBackNodes;

    private long paidRent = NO_RENT_TO_PAY;
    private long rollbacksFees = NO_ROLLBACK_YET;

    public StorageRentManager() {
        this(new HashSet<>(), new ArrayList<>());
    }

    public StorageRentManager(Collection<RentedNode> storageRentNodes, List<RentedNode> rolledBackNodes) {
        this.rentedNodes = storageRentNodes;
        this.rolledBackNodes = rolledBackNodes;
    }

    /**
     * Pay storage rent.
     *
     * @param gasRemaining remaining gas amount to pay storage rent
     * @param executionBlockTimestamp execution block timestamp
     * @param repository repository to fetch the relevant data and to update the rent timestamps
     * @return new remaining gas
     * */
    public long pay(long gasRemaining, long executionBlockTimestamp, Repository repository) {
        // fetch relevant data to pay storage rent
        fetchStorageRentData(repository, rentedNodes);
        // fetch relevant data to pay fees for rollbacks
        fetchStorageRentData(repository, rolledBackNodes);

        // todo(fedejinich) should i add a check for rented nodes > 0?

        long payableRent = rentBy(this.rentedNodes, rentedNode -> rentedNode.payableRent(executionBlockTimestamp));
        long rollbacksRent = rentBy(this.rolledBackNodes, rentedNode -> rentedNode.rollbackFee(executionBlockTimestamp));

//        long payableRent = this.rentedNodes.isEmpty() ? 0 : this.rentedNodes.stream()
//                .map(v -> v.payableRent(executionBlockTimestamp))
//                .reduce(GasCost::add)
//                .get();

//        long rollbacksRent = rolledBackNodes.isEmpty() ? 0 : rolledBackNodes.stream()
//                .map(v -> v.rollbackFee(executionBlockTimestamp))
//                .reduce(GasCost::add)
//                .get();

        long totalRent = payableRent + rollbacksRent;

        if(totalRent <= 0) {
            // todo(fedejinich) should throw the right exception
            throw new RuntimeException("totalRent should be positive");
        }

        if(gasRemaining < totalRent) {
            // todo(fedejinich) this is not the right way to throw an OOG
            throw new Program.OutOfGasException("not enough gasRemaining to pay storage rent. gasRemaining: {}, gasNeeded: {}");
        }

        this.rentedNodes.forEach(node -> repository.updateLastRentPaidTimestamp(
                node.getKey().getData(),
                node.updatedRentTimestamp(executionBlockTimestamp)));

        this.paidRent = totalRent;
        this.rollbacksFees = rollbacksRent;

        return GasCost.subtract(gasRemaining, this.paidRent);
    }

    private long rentBy(Collection<RentedNode> rentedNodeCollection, Function<RentedNode, Long> rentMapFunction) {
        return rentedNodeCollection.isEmpty() ? 0 : rentedNodeCollection.stream()
                .map(r -> rentMapFunction.apply(r))
                .reduce(GasCost::add)
                .get();
    }

    // todo(fedejinich) this can be improved, to me it makes sense
    private void fetchStorageRentData(Repository repository, Collection<RentedNode> nodes) {
        nodes.forEach(node -> {
            Map<String, Long> storageRentData = repository.fetchStorageRentData(node.getKey().getData());
            node.setStorageRentData(storageRentData.get("nodeSize"), storageRentData.get("lastRentPaidTimestamp"));
        });
    }

    @VisibleForTesting
    public long getPaidRent() {
        return this.paidRent;
    }

    @VisibleForTesting
    public long getRollbacksFees() {
        return this.rollbacksFees;
    }

    @VisibleForTesting
    public Collection<RentedNode> getRentedNodes() {
        return this.rentedNodes;
    }

    @VisibleForTesting
    public List<RentedNode> getRolledBackNodes() {
        return this.rolledBackNodes;
    }
}
