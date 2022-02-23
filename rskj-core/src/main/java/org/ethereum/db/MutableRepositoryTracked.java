package org.ethereum.db;

import co.rsk.core.RskAddress;
import co.rsk.core.types.ints.Uint24;
import co.rsk.crypto.Keccak256;
import co.rsk.trie.MutableTrie;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.Repository;
import org.ethereum.vm.DataWord;

import java.util.*;

import static org.ethereum.db.OperationType.*;

public class MutableRepositoryTracked extends MutableRepository {

    // a set to track all the used trie-value-containing nodes in this repository (and its children repositories)
    private final Set<TrackedNode> trackedNodes;
    // a list to track mismatched trie-keys
    private final List<ByteArrayWrapper> mismatches; // todo(fedejinich) this might be removed
    // parent repository to commit tracked nodes
    private final Repository parentRepository;

    public MutableRepositoryTracked(MutableTrie mutableTrie, Repository parentRepository) {
        super(mutableTrie);
        this.parentRepository = parentRepository;
        this.trackedNodes = new HashSet<>();
        this.mismatches = new ArrayList<>();
    }

    protected void internalPut(byte[] key, byte[] value) {
        super.internalPut(key, value);
        if(value != null) { // there's no need to track if it's a delete operation
            trackNode(key, WRITE_OPERATION);
        }
        // todo(fedejinich) there are no mismatches for put operations, right?
    }

    protected byte[] internalGet(byte[] key) {
        byte[] value = super.internalGet(key);

        if(value != null) {
            trackNode(key, READ_OPERATION);
        } else {
            trackMismatch(key);
        }

        return value;
    }

    protected Optional<Keccak256> internalGetValueHash(byte[] key) {
        Optional<Keccak256> valueHash = super.internalGetValueHash(key);

        if(valueHash.isPresent()) {
            trackNode(key, READ_OPERATION);
        } else {
            trackMismatch(key);
        }

        return valueHash;
    }

    protected Uint24 internalGetValueLength(byte[] key) {
        Uint24 valueLength = super.internalGetValueLength(key);

        if(valueLength != Uint24.ZERO) {
            trackNode(key, READ_OPERATION);
        } else {
            trackMismatch(key);
        }

        return valueLength;
    }

    protected Iterator<DataWord> internalGetStorageKeys(RskAddress addr) {
        Iterator<DataWord> storageKeys = super.internalGetStorageKeys(addr);

        byte[] accountStoragePrefixKey = trieKeyMapper.getAccountStoragePrefixKey(addr); // todo(fedejinich) is this ok ?
        if(!storageKeys.equals(Collections.emptyIterator())) { // todo(fedejinich) is this ok?
            trackNode(accountStoragePrefixKey, READ_OPERATION);
        } else {
            trackMismatch(accountStoragePrefixKey);
        }

        return storageKeys;
    }

    protected void internalDeleteRecursive(byte[] key) {
        // todo(fedejinich) if we remove mismatches we can avoid the overhead of find+delete
        boolean exists = this.mutableTrie.find(key) != null;
        if(exists) {
            trackNode(key, DELETE_OPERATION);
        } else {
            trackMismatch(key);
        }

        super.internalDeleteRecursive(key);
    }

    /**
     * Tracks the relevant data
     *
     * @param rawKey an existing trie key
     * @param operationType an operation type (read/write)
     * */
    @VisibleForTesting
    public void trackNode(byte[] rawKey, OperationType operationType) {
        // todo(fedejinich) NEED TO DEFINE WHEN/HOW TRACK CONTRACT CODES OPERATIONS
        this.trackedNodes.add(new TrackedNode(new ByteArrayWrapper(rawKey), operationType, false));
    }

    @VisibleForTesting
    public void trackMismatch(byte[] key) {
        this.mismatches.add(new ByteArrayWrapper(key));
    }
}
