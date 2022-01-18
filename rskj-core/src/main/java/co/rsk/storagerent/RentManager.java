package co.rsk.storagerent;

import org.ethereum.core.Repository;
import org.ethereum.vm.GasCost;
import org.ethereum.vm.program.Program;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class RentManager {
    private final List<RentTrackingElement> trackedNodes = new ArrayList<>();

    public void trackNode(RentTrackingElement rentTrackingElement) {
        this.trackedNodes.add(rentTrackingElement);
    }

    /**
     * Pays storage rent (RSKIP240)
     *
     * @param gas initial gas amount
     * @param repository a repository to fetch the relevant data and to update the storage rent timestamps
     * @return gas leftover
     * */
    // todo(fedejinich)
    //  - If state changes are reverted at any call depth, due to an error,
    //     then all associated rent updates must also be reverted
    //  - If a transaction ends because of a OOG exception or REVERT, then 25% of the storage rent gas used so far
    //      is consumed as compensation for computation and IO costs.
    //  - If an error at some call depth is handled in a way that transaction execution can proceed,
    //      then only the timestamp changes associated with the failed call (and its subcalls) need to be reverted.
    //      Again, 75% of gas used for rent in the failed call should be refunded to the caller.
    public long payStorageRent(long gas, long currentBlockTimestamp, Repository repository) {
        fetchStorageRentData(repository); // fetch relevant data to pay storage rent

        long cumulativeRent = cumulativeRent();

        if(gas < cumulativeRent) { // todo(fedejinich) is this the right way to throw an OOG?
            throw new Program.OutOfGasException("not enough gas to pay storage rent. gas: {}, gasNeeded: {}");
        }

        trackedNodes.forEach(e -> {
            repository.updateLastRentPaidTimestamp(
                    e.getKey().encode(),
                    e.updatedRentTimestamp(currentBlockTimestamp)); // todo(fedejinich) maybe we can rename this
        });

        return GasCost.subtract(gas, cumulativeRent);
    }

    private long cumulativeRent() {
        return trackedNodes
                .stream()
                .map(RentTrackingElement::payableRent)
                .reduce(GasCost::add)
                .get(); // todo(fedejinich) this should always be present, should I throw an exception?
    }

    private void fetchStorageRentData(Repository repository) {
        this.trackedNodes.forEach(rentTrackingElement -> {
            Map<String, Long> storageRentData = repository.fetchStorageRentData(rentTrackingElement.getKey().encode());

            rentTrackingElement.setNodeSize(storageRentData.get("nodeSize"));
            rentTrackingElement.setLastRentPaidTimestamp(storageRentData.get("lastRentPaidTimestamp"));
        });
    }
}
