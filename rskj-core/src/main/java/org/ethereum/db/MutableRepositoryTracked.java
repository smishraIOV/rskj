package org.ethereum.db;

import co.rsk.core.RskAddress;
import co.rsk.core.types.ints.Uint24;
import co.rsk.crypto.Keccak256;
import co.rsk.trie.MutableTrie;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.vm.DataWord;

import java.util.Collections;
import java.util.Iterator;
import java.util.Optional;

import static org.ethereum.db.OperationType.*;

public class MutableRepositoryTracked extends MutableRepository {
    public MutableRepositoryTracked(MutableTrie mutableTrie) {
        super(mutableTrie);
    }

    protected void internalPut(byte[] key, byte[] value) {
        mutableTrie.put(key, value);
        if(value != null) { // there's no need to track if it's a delete operation
            trackNode(key, WRITE_OPERATION);
        }
        // todo(fedejinich) there are no mismatches for put operations, right?
    }

    protected byte[] internalGet(byte[] key) {
        byte[] value = mutableTrie.get(key);

        if(value != null) {
            trackNode(key, READ_OPERATION);
        } else {
            trackMismatch(key);
        }

        return value;
    }

    protected Optional<Keccak256> internalGetValueHash(byte[] key) {
        Optional<Keccak256> valueHash = mutableTrie.getValueHash(key);

        if(valueHash.isPresent()) {
            trackNode(key, READ_OPERATION);
        } else {
            trackMismatch(key);
        }

        return valueHash;
    }

    protected Uint24 internalGetValueLength(byte[] key) {
        Uint24 valueLength = mutableTrie.getValueLength(key);

        if(valueLength != Uint24.ZERO) {
            trackNode(key, READ_OPERATION);
        } else {
            trackMismatch(key);
        }

        return valueLength;
    }

    protected Iterator<DataWord> internalGetStorageKeys(RskAddress addr) {
        // this method has been created to encapsulate mutableTrie.getStorageKeys(),
        // and adds tracking logic for storage rent
        Iterator<DataWord> storageKeys = mutableTrie.getStorageKeys(addr);
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
        mutableTrie.deleteRecursive(key);
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
