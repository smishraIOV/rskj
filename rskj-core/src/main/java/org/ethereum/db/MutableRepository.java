/*
 * This file is part of RskJ
 * Copyright (C) 2019 RSK Labs Ltd.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package org.ethereum.db;

import co.rsk.core.Coin;
import co.rsk.core.RskAddress;
import co.rsk.core.types.ints.Uint24;
import co.rsk.crypto.Keccak256;
import co.rsk.db.MutableTrieCache;
import co.rsk.db.MutableTrieImpl;
import co.rsk.storagerent.RentedNode;
import co.rsk.trie.MutableTrie;
import co.rsk.trie.Trie;
import co.rsk.trie.TrieKeySlice;
import co.rsk.trie.TrieStore;
import co.rsk.util.HexUtils;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.core.AccountState;
import org.ethereum.core.Repository;
import org.ethereum.crypto.Keccak256Helper;
import org.ethereum.vm.DataWord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.math.BigInteger;
import java.util.*;
import java.util.stream.Collectors;

import static co.rsk.trie.Trie.NO_RENT_TIMESTAMP;
import static org.ethereum.db.OperationType.*;

public class MutableRepository implements Repository {
    private static final Logger LOGGER_FEDE = LoggerFactory.getLogger(MutableRepository.class);

    private static final Logger logger = LoggerFactory.getLogger("repository");
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[0];
    public static final Keccak256 KECCAK_256_OF_EMPTY_ARRAY = new Keccak256(Keccak256Helper.keccak256(EMPTY_BYTE_ARRAY));
    private static final byte[] ONE_BYTE_ARRAY = new byte[] { 0x01 };

    protected final TrieKeyMapper trieKeyMapper;
    protected final MutableTrie mutableTrie;



    // todo(fedejinich) ALL THIS MEMBERS MIGHT/SHOULD BE MOVED TO MutableRepositoryTracked

    // a set to track all the used trie-value-containing nodes in this repository (and its children repositories)
    protected final Set<TrackedNode> trackedNodes;
    // a list to track mismatched trie-keys
    protected final List<ByteArrayWrapper> mismatches; // todo(fedejinich) this might be removed
    // a list of nodes tracked nodes that were rolled back (due to revert or OOG)
    protected final List<TrackedNode> rolledBackNodes;
    // parent repository to commit tracked nodes
    protected final Repository parentRepository;

    public MutableRepository(TrieStore trieStore, Trie trie) {
        this(new MutableTrieImpl(trieStore, trie));
    }

    public MutableRepository(MutableTrie mutableTrie) {
        this(mutableTrie, null);
    }

    // todo(fedejinich) default constructor will be MutableRepository(MutableTrie mutableTrie)
    public MutableRepository(MutableTrie mutableTrie, Repository parentRepository) {
        this.trieKeyMapper = new TrieKeyMapper();
        this.mutableTrie = mutableTrie;
        this.parentRepository = parentRepository; // todo(fedejinich) this will be moved to MutableRepositoryTracked
        this.trackedNodes = new HashSet<>(); // todo(fedejinich) this will be moved to MutableRepositoryTracked
        this.mismatches = new ArrayList<>(); // todo(fedejinich) this will be moved to MutableRepositoryTracked
        this.rolledBackNodes = new ArrayList<>(); // todo(fedejinich) this will be moved to MutableRepositoryTracked
    }

    @Override
    public Trie getTrie() {
        return mutableTrie.getTrie();
    }

    @Override
    public synchronized AccountState createAccount(RskAddress addr) {
        AccountState accountState = new AccountState();
        updateAccountState(addr, accountState);
        return accountState;
    }

    @Override
    public synchronized void setupContract(RskAddress addr) {
        byte[] prefix = trieKeyMapper.getAccountStoragePrefixKey(addr);
        internalPut(prefix, ONE_BYTE_ARRAY);
    }

    @Override
    public synchronized boolean isExist(RskAddress addr) {
        // Here we assume size != 0 means the account exists
        return internalGetValueLength(trieKeyMapper.getAccountKey(addr))
                .compareTo(Uint24.ZERO) > 0;
    }

    @Override
    public synchronized AccountState getAccountState(RskAddress addr) {
        AccountState result = null;
        byte[] accountData = getAccountData(addr);

        // If there is no account it returns null
        if (accountData != null && accountData.length != 0) {
            result = new AccountState(accountData);
        }
        return result;
    }

    @Override
    public synchronized void delete(RskAddress addr) {
        internalDeleteRecursive(trieKeyMapper.getAccountKey(addr));
    }

    @Override
    public synchronized void hibernate(RskAddress addr) {
        AccountState account = getAccountStateOrCreateNew(addr);

        account.hibernate();
        updateAccountState(addr, account);
    }

    @Override
    public void setNonce(RskAddress addr,BigInteger nonce) {
        AccountState account = getAccountStateOrCreateNew(addr);

        account.setNonce(nonce);
        updateAccountState(addr, account);
    }

    @Override
    public synchronized BigInteger increaseNonce(RskAddress addr) {
        AccountState account = getAccountStateOrCreateNew(addr);

        account.incrementNonce();
        updateAccountState(addr, account);
        return account.getNonce();
    }

    @Override
    public synchronized BigInteger getNonce(RskAddress addr) {
        // Why would getNonce create an Account in the repository? The semantic of a get()
        // is clear: do not change anything!
        AccountState account = getAccountState(addr);
        if (account == null) {
            return BigInteger.ZERO;
        }

        return account.getNonce();
    }

    @Override
    public synchronized void saveCode(RskAddress addr, byte[] code) {
        byte[] key = trieKeyMapper.getCodeKey(addr);
        internalPut(key, code);

        if (code != null && code.length != 0 && !isExist(addr)) {
            createAccount(addr);
        }
    }

    @Override
    public synchronized int getCodeLength(RskAddress addr) {
        AccountState account = getAccountState(addr);
        if (account == null || account.isHibernated()) {
            return 0;
        }

        byte[] key = trieKeyMapper.getCodeKey(addr);
        return internalGetValueLength(key)
                .intValue();
    }

    @Override
    public synchronized Keccak256 getCodeHashNonStandard(RskAddress addr) {

        if (!isExist(addr)) {
            return Keccak256.ZERO_HASH;
        }

        if (!isContract(addr)) {
            return KECCAK_256_OF_EMPTY_ARRAY;
        }

        byte[] key = trieKeyMapper.getCodeKey(addr);
        Optional<Keccak256> valueHash = internalGetValueHash(key);

        //Returning ZERO_HASH is the non standard implementation we had pre RSKIP169 implementation
        //and thus me must honor it.
        return valueHash.orElse(Keccak256.ZERO_HASH);
    }

    @Override
    public synchronized Keccak256 getCodeHashStandard(RskAddress addr) {

        if (!isExist(addr)) {
            return Keccak256.ZERO_HASH;
        }

        if (!isContract(addr)) {
            return KECCAK_256_OF_EMPTY_ARRAY;
        }

        byte[] key = trieKeyMapper.getCodeKey(addr);

        return internalGetValueHash(key)
                .orElse(KECCAK_256_OF_EMPTY_ARRAY);
    }

    @Override
    public synchronized byte[] getCode(RskAddress addr) {
        if (!isExist(addr)) {
            return EMPTY_BYTE_ARRAY;
        }

        AccountState account = getAccountState(addr);
        if (account.isHibernated()) {
            return EMPTY_BYTE_ARRAY;
        }

        byte[] key = trieKeyMapper.getCodeKey(addr);
        return internalGet(key);
    }

    @Override
    public boolean isContract(RskAddress addr) {
        byte[] prefix = trieKeyMapper.getAccountStoragePrefixKey(addr);
        return internalGet(prefix) != null;
    }

    @Override
    public synchronized void addStorageRow(RskAddress addr, DataWord key, DataWord value) {
        // DataWords are stored stripping leading zeros.
        addStorageBytes(addr, key, value.getByteArrayForStorage());
    }

    @Override
    public synchronized void addStorageBytes(RskAddress addr, DataWord key, byte[] value) {
        // This should not happen in production because contracts are created before storage cells are added to them.
        // But it happens in Repository tests, that create only storage row cells.
        if (!isExist(addr)) {
            createAccount(addr);
            setupContract(addr);
        }

        byte[] triekey = trieKeyMapper.getAccountStorageKey(addr, key);

        // Special case: if the value is an empty vector, we pass "null" which commands the trie to remove the item.
        // Note that if the call comes from addStorageRow(), this method will already have replaced 0 by null, so the
        // conversion here only applies if this is called directly. If suppose this only occurs in tests, but it can
        // also occur in precompiled contracts that store data directly using this method.
        if (value == null || value.length == 0) {
            internalPut(triekey, null);
        } else {
            internalPut(triekey, value);
        }
    }

    @Override
    public synchronized DataWord getStorageValue(RskAddress addr, DataWord key) {
        byte[] triekey = trieKeyMapper.getAccountStorageKey(addr, key);
        byte[] value = internalGet(triekey);
        if (value == null) {
            return null;
        }

        return DataWord.valueOf(value);
    }

    @Override
    public synchronized byte[] getStorageBytes(RskAddress addr, DataWord key) {
        byte[] triekey = trieKeyMapper.getAccountStorageKey(addr, key);
        return internalGet(triekey);
    }

    @Override
    public Iterator<DataWord> getStorageKeys(RskAddress addr) {
        // -1 b/c the first bit is implicit in the storage node
        return internalGetStorageKeys(addr);
    }

    @Override
    public int getStorageKeysCount(RskAddress addr) {
        // FIXME(diegoll): I think it's kind of insane to iterate the whole tree looking for storage keys for this address
        //  I think we can keep a counter for the keys, using the find function for detecting duplicates and so on
        int storageKeysCount = 0;
        Iterator<DataWord> keysIterator = getStorageKeys(addr);
        for(;keysIterator.hasNext(); keysIterator.next()) {
            storageKeysCount ++;
        }
        return storageKeysCount;
    }

    @Override
    public synchronized Coin getBalance(RskAddress addr) {
        AccountState account = getAccountState(addr);
        return (account == null) ? Coin.ZERO: account.getBalance();
    }

    @Override
    public synchronized Coin addBalance(RskAddress addr, Coin value) {
        AccountState account = getAccountStateOrCreateNew(addr);

        Coin result = account.addToBalance(value);
        updateAccountState(addr, account);

        return result;
    }

    @Override
    public synchronized Set<RskAddress> getAccountsKeys() {
        Set<RskAddress> result = new HashSet<>();
        //TODO(diegoll): this is needed when trie is a MutableTrieCache, check if makes sense to commit here
        mutableTrie.commit();
        Trie trie = mutableTrie.getTrie();
        Iterator<Trie.IterationElement> preOrderIterator = trie.getPreOrderIterator(true);
        while (preOrderIterator.hasNext()) {
            TrieKeySlice nodeKey = preOrderIterator.next().getNodeKey();
            int nodeKeyLength = nodeKey.length();
            if (nodeKeyLength == (1 + TrieKeyMapper.SECURE_KEY_SIZE + RskAddress.LENGTH_IN_BYTES) * Byte.SIZE) {
                byte[] address = nodeKey.slice(nodeKeyLength - RskAddress.LENGTH_IN_BYTES * Byte.SIZE, nodeKeyLength).encode();
                result.add(new RskAddress(address));
            }
        }
        return result;
    }

    // To start tracking, a new repository is created, with a MutableTrieCache in the middle
    @Override
    public synchronized Repository startTracking() {
        return new MutableRepository(new MutableTrieCache(this.mutableTrie), this);
    }

    @Override
    public void save() {
        this.mutableTrie.save();
    }

    @Override
    public synchronized void commit() {
        this.mutableTrie.commit();

        // todo(fedejinich) this is unnecessary but there is a techdebt at BlockChainBuilder:177
        //  only repositories with caches should rollback/commit
        if(this.parentRepository != null) {
            this.parentRepository.mergeTrackedNodes(this.trackedNodes, this.mismatches);
            this.parentRepository.addRolledBackNodes(this.rolledBackNodes);
        }
    }

    @Override
    public synchronized void rollback() {
        this.mutableTrie.rollback();

        // todo(fedejinich) this is unnecessary but there is a techdebt at BlockChainBuilder:177
        //  only repositories with caches should rollback/commit
        if(parentRepository != null) {
            this.parentRepository.addRolledBackNodes(this.trackedNodes);
            this.trackedNodes.clear();
            this.mismatches.clear();
            this.rolledBackNodes.clear();
        }
    }

    @Override
    public synchronized byte[] getRoot() {
        mutableTrie.save();

        Keccak256 rootHash = mutableTrie.getHash();
        logger.trace("getting repository root hash {}", rootHash);
        return rootHash.getBytes();
    }

    @Override
    public synchronized void updateAccountState(RskAddress addr, final AccountState accountState) {
        byte[] accountKey = trieKeyMapper.getAccountKey(addr);
        internalPut(accountKey, accountState.getEncoded());
    }

    @Nonnull
    private synchronized AccountState getAccountStateOrCreateNew(RskAddress addr) {
        AccountState account = getAccountState(addr);
        return (account == null) ? createAccount(addr) : account;
    }

    private byte[] getAccountData(RskAddress addr) {
        return internalGet(trieKeyMapper.getAccountKey(addr));
    }

    // todo(fedejinich) ideally all of the down content should be moved to MutableRepositoryTracking

    @Override
    public void updateLastRentPaidTimestamp(byte[] key, long updatedRentTimestamp) {
        // todo(fedejinich) I guess I can avoid TrieKeySlice.fromKey(key)
        this.mutableTrie.updateLastRentPaidTimestamp(TrieKeySlice.fromKey(key), updatedRentTimestamp);
    }
    
    @Override
    public Map<String, Long> fetchStorageRentData(byte[] key) {
        MutableTrie resultFromTrie = this.mutableTrie.find(key);

        // the key exists in the cache, but is not present in the
        // trie before executing the transaction, therefore it's a new node
        boolean newNode = resultFromTrie.getTrie() == null && this.mutableTrie.get(key) != null;
        long nodeSize = newNode ? this.mutableTrie.getValueLength(key).intValue() : resultFromTrie.getNodeSize();

        // todo(fedejinich) nodes can be storage cell or account state, maybe we can return fixed sizes from this cases

        String s = HexUtils.toJsonHex(key);
        LOGGER_FEDE.error("key: {}, isNewNode: {}", s.substring(s.length() - 5), newNode);
        LOGGER_FEDE.error("nodeSize: {}", nodeSize);

        Map<String, Long> storageRentData = new HashMap<>();
        // todo(fedejinich) is this ok? (getValueLength) this is not ok, it should be the length of the serialized node
        storageRentData.put("nodeSize", nodeSize);
        storageRentData.put("lastRentPaidTimestamp", newNode ? NO_RENT_TIMESTAMP : resultFromTrie.getLastRentPaidTimestamp());

        return storageRentData;
    }

    // Internal methods contains node tracking

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

    @Override
    public void mergeTrackedNodes(Set<TrackedNode> trackedNodes, List<ByteArrayWrapper> mismatches) {
        this.trackedNodes.addAll(trackedNodes);
        this.mismatches.addAll(mismatches);
    }

    @Override
    public Collection<RentedNode> getStorageRentNodes() {
        Map<ByteArrayWrapper, RentedNode> rentedNodes = new HashMap<>();
        this.trackedNodes.forEach(trackedNode -> {
            ByteArrayWrapper key = new ByteArrayWrapper(trackedNode.getKey().getData());
            RentedNode rentedNode = new RentedNode(trackedNode);
            RentedNode containedNode = rentedNodes.get(key);
            if(containedNode != null) {
                if(containedNode.shouldBeReplaced(rentedNode)) {
                    rentedNodes.put(key, rentedNode);
                }
            } else {
                rentedNodes.put(key, rentedNode);
            }
        });

        return rentedNodes.values();
    }

    @Override
    public void addRolledBackNodes(Collection<TrackedNode> trackedNodes) {
        this.rolledBackNodes.addAll(trackedNodes);
    }

    @Override
    public List<RentedNode> getRolledBackNodes() {
        return this.rolledBackNodes.stream().map(trackedNode -> new RentedNode(trackedNode)).collect(Collectors.toList());
    }
}
