package co.rsk.storagerent;

import org.ethereum.vm.GasCost;

import java.util.ArrayList;
import java.util.List;

public class RentManager {
    private final List<RentTrackingElement> trackedNodes = new ArrayList<>();

    public long cumulativeRent() {
        long cumulativeRent = trackedNodes
                .stream()
                .map(RentTrackingElement::payableRent)
                .reduce(GasCost::add)
                .get(); // todo(fedejinich) should I throw an exception?
        return cumulativeRent;
    }

    public void trackNode(RentTrackingElement rentTrackingElement) {
        this.trackedNodes.add(rentTrackingElement);
    }

    public List<RentTrackingElement> getTrackedNodes() {
        return this.trackedNodes;
    }
}
