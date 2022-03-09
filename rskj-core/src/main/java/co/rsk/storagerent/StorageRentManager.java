package co.rsk.storagerent;

import co.rsk.util.HexUtils;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Repository;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.program.Program;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Function;

/**
 * StorageRentManager, responsible for paying StorageRent (RSKIP240)
 * https://github.com/rsksmart/RSKIPs/blob/master/IPs/RSKIP240.md
 * */
public class StorageRentManager {
    private static final Logger LOGGER_FEDE = LoggerFactory.getLogger("fede");

    public static final long NO_RENT_TO_PAY = -1;
    public static final long NO_ROLLBACK_YET = -1;

    // todo(shree) this should be append only (no rollbacks)
    private final Collection<RentedNode> rentedNodes;
    // todo(shree) State changes that are rolled back for some reason (25% of computed-rent collected for extra work)
    private final List<RentedNode> rolledBackNodes;

    private long paidRent = NO_RENT_TO_PAY;
    private long rollbacksRent = NO_ROLLBACK_YET;

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

        if(rentedNodes.isEmpty() && rolledBackNodes.isEmpty()) {
            throw new RuntimeException("there should be rented nodes or rollback nodes");
        }

        long payableRent = rentBy(this.rentedNodes, rentedNode -> rentedNode.payableRent(executionBlockTimestamp));
        // todo(shree) rollbackfee already incorporates the idea of retaining only 25%
        // Q: However, we check that rolledBackNodes do not contain anything ..
        //      .. for which rent is collected above (do not pay more than once)? Bcos it is possible that a node is 
        //      .. touched by multiple internal tx but rolled back in one of them
        long rollbacksRent = rentBy(this.rolledBackNodes, rentedNode -> rentedNode.rollbackFee(executionBlockTimestamp));

        LOGGER_FEDE.error("payableRent: {}, rollbacksRent: {}", payableRent, rollbacksRent);

        long totalRent = payableRent + rollbacksRent;

        LOGGER_FEDE.error("totalRent: {}", totalRent);

//        if(totalRent <= 0) {
//            // todo(fedejinich) should throw the right exception
//            throw new RuntimeException("totalRent should be positive");
//        }

        LOGGER_FEDE.error("gasRemaining({}) < totalRent({})", gasRemaining, totalRent);

        if(gasRemaining < totalRent) {
            // todo(fedejinich) this is not the right way to throw an OOG
            throw new Program.OutOfGasException("not enough gasRemaining to pay storage rent. gasRemaining: {}, gasNeeded: {}");
        }

        LOGGER_FEDE.error("executionBlockTimestamp: {}", executionBlockTimestamp);

        this.rentedNodes.forEach(node -> {
            long updatedRentTimestamp = node.updatedRentTimestamp(executionBlockTimestamp);
            long oldTimestamp = node.getLastRentPaidTimestamp();

            repository.updateLastRentPaidTimestamp(
                node.getKey().getData(),
                    updatedRentTimestamp);

            LOGGER_FEDE.error("updated timestamp - node: {}, oldTimestamp: {}, updatedRentTimestamp: {}",
                    printableKey(node), oldTimestamp, updatedRentTimestamp);
        });

        this.paidRent = totalRent;
        this.rollbacksRent = rollbacksRent;

        LOGGER_FEDE.error("paidRent: {}, rollbacksRent: {}", this.paidRent, this.rollbacksRent);

        return GasCost.subtract(gasRemaining, this.paidRent);
    }

    // todo(fedejinich) this is for debugging
    private String printableKey(RentedNode node) {
        String s = HexUtils.toJsonHex(node.getKey().getData());
        return s.substring(s.length() - 5);
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

            Long nodeSize = storageRentData.get("nodeSize");
            Long lastRentPaidTimestamp = storageRentData.get("lastRentPaidTimestamp");

            node.setStorageRentData(nodeSize, lastRentPaidTimestamp);

            LOGGER_FEDE.error("fetched storage rent data - node: {}, nodeSize: {}, timestamp: {}",
                    printableKey(node), nodeSize, lastRentPaidTimestamp);
        });
    }

    @VisibleForTesting
    public long getPaidRent() {
        return this.paidRent;
    }

    @VisibleForTesting
    public long getRollbacksRent() {
        return this.rollbacksRent;
    }

    @VisibleForTesting
    public Collection<RentedNode> getRentedNodes() {
        return this.rentedNodes;
    }

    @VisibleForTesting
    public List<RentedNode> getRolledBackNodes() {
        return this.rolledBackNodes;
    }

    /**
     * Commits nodes from another storage rent manager, which is used to track nodes from internal transactions
     * @param temporaryStorageRentManager a temporary rent manager, only used to track nodes
     * 
    public void commit(StorageRentManager temporaryStorageRentManager) {
        // todo(shree): 
        //      1. delete "values()" 
        //      2. isn't this just a merger of two collections? Perhaps we need the logic of `shouldBeReplaced` 
        //      3. trackNode() is not defined here 
        temporaryStorageRentManager.rentedNodes.values().forEach(node -> trackNode(node));
    } */
}
