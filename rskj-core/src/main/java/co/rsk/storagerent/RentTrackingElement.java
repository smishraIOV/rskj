package co.rsk.storagerent;

import co.rsk.trie.Trie;
import co.rsk.trie.TrieKeySlice;

import static co.rsk.storagerent.RentTrackingElement.TrackingOperation.WRITE_OPERATION;

public class RentTrackingElement {
    public enum TrackingOperation { WRITE_OPERATION, READ_OPERATION }

    private final TrieKeySlice key;
    private final Trie trackedNode;
    private final TrackingOperation trackingOperation;

    public RentTrackingElement(TrieKeySlice key, Trie trackedNode, TrackingOperation trackingOperation) {
        this.key = key;
        this.trackedNode = trackedNode;
        this.trackingOperation = trackingOperation;
    }

    public long payableRent() {
        return StorageRentComputation.computeRent(this.rentDue(), this.rentCap(), this.rentThreshold());
    }

    public long updatedRentTimestamp(long currentBlockTimsetamp) {
        return StorageRentComputation.computeNewTimestamp(
                this.nodeSize(),
                this.rentDue(),
                this.trackedNode.getLastRentPaidTimestamp(),
                currentBlockTimsetamp,
                this.rentCap(),
                this.rentThreshold()
        );
    }

    public TrieKeySlice getKey() {
        return this.key;
    }

    private long rentThreshold() {
        // todo(fedejinich) still need to distinguish contract threshold
        return trackingOperation == WRITE_OPERATION ?
                StorageRentComputation.WRITE_THRESHOLD : StorageRentComputation.READ_THRESHOLD;
    }

    private long rentDue() {
        return StorageRentComputation.rentDue(
                nodeSize(), this.trackedNode.getLastRentPaidTimestamp());
    }

    private long rentCap() {
        // todo(fedejinich) still need to distinguish contract rent caps
        return StorageRentComputation.RENT_CAP;
    }

    private int nodeSize() {
        // todo(fedejinich) is this the right way to get the node size?
        //  also message length triggers internalToMessage() which changes the internal trie state,
        //  maybe I can get the size at the object creation.
        //  also, delete this method when the time comes, it's only a pasamanos
        return trackedNode.getMessageLength();
    }
}
