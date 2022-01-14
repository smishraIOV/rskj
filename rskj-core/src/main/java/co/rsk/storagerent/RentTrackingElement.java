package co.rsk.storagerent;

import co.rsk.trie.Trie;
import co.rsk.trie.TrieKeySlice;
import org.apache.commons.lang3.NotImplementedException;

public class RentTrackingElement {
    public enum TrackingOperation { WRITE_OPERATION, READ_OPERATION }

    private TrieKeySlice key;
    private Trie trackedNode;
    private TrackingOperation trackingOperation;

    public RentTrackingElement(TrieKeySlice key, Trie trackedNode, TrackingOperation trackingOperation) {
        this.key = key;
        this.trackedNode = trackedNode;
        this.trackingOperation = trackingOperation;
    }

    public long payableRent() {
        throw new NotImplementedException("needs to be implemented");
    }

    public long updatedRentTimestamp() {
        throw new NotImplementedException("needs to be implemented");
    }

    public TrieKeySlice getKey() {
        return this.key;
    }
}
