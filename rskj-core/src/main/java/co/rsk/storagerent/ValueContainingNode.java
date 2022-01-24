package co.rsk.storagerent;

import co.rsk.trie.TrieKeySlice;

import static co.rsk.storagerent.ValueContainingNode.OperationType.WRITE_OPERATION;
import static co.rsk.storagerent.StorageRentComputation.*;

/**
 * Represents an existing value-containing trie node.
 * Given an existing trie key, returns the relevant data for paying the storage rent.
 * */
public class ValueContainingNode {

    public enum OperationType { WRITE_OPERATION, READ_OPERATION }

    private final TrieKeySlice key;
    private final OperationType operationType;
    private final boolean loadsContractCode;

    private Long nodeSize;
    private Long lastRentPaidTimestamp;

    // todo(fedejinich) WRITE OPERATION & LOADS CONTRACT CODE, IS THAT AN ILLEGAL STATE?
    public ValueContainingNode(TrieKeySlice key, OperationType operationType, boolean loadsContractCode) {
        this.key = key;
        this.operationType = operationType;
        this.loadsContractCode = loadsContractCode;
    }

    /**
     * Calculates the payable rent amount (the total amount it's limited by the rent cap)
     * @return the rent amount expressed in gas units
     * */
    public long payableRent(long currentBlockTimestamp) {
        return computeRent(
                rentDue(getNodeSize(), duration(currentBlockTimestamp)),
                rentCap(loadsContractCode),
                rentThreshold(loadsContractCode));
    }

    /**
     * Calculates the new timestamp after paying the rent
     * @return a new updated timestamp
     * */
    public long updatedRentTimestamp(long currentBlockTimsetamp) {
        return computeNewTimestamp(
                getNodeSize(),
                rentDue(getNodeSize(), duration(currentBlockTimsetamp)),
                getLastRentPaidTimestamp(),
                currentBlockTimsetamp,
                rentCap(loadsContractCode),
                rentThreshold(loadsContractCode)
        );
    }

    public TrieKeySlice getKey() {
        return this.key;
    }

    private long getNodeSize() {
        if(nodeSize == null) {
            throw new IllegalStateException("no nodeSize, initialize it by fetching storage rent data with the RentManager");
        }
        return nodeSize;
    }

    private long getLastRentPaidTimestamp() {
        if(lastRentPaidTimestamp == null) {
            throw new IllegalStateException("no lastRentPaidTimestamp, initialize it by fetching storage rent data with the RentManager");
        }
        return lastRentPaidTimestamp;
    }

    public void setNodeSize(Long nodeSize) {
        this.nodeSize = nodeSize;
    }

    public void setLastRentPaidTimestamp(Long lastRentPaidTimestamp) {
        this.lastRentPaidTimestamp = lastRentPaidTimestamp;
    }

    private long duration(long currentBlockTimsetamp) {
        // todo(fedejinich) is this the right way to calculate the duration?
        return currentBlockTimsetamp - this.getLastRentPaidTimestamp();
    }

    private long rentThreshold(boolean loadsContractCode) {
        return operationType == WRITE_OPERATION ? WRITE_THRESHOLD
                : (loadsContractCode ? READ_THRESHOLD_CONTRACT_CODE : READ_THRESHOLD);
    }

    private long rentCap(boolean loadsContract) {
        return loadsContract ? RENT_CAP_CONTRACT_CODE : RENT_CAP;
    }
}
