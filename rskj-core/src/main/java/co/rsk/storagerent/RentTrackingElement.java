package co.rsk.storagerent;

import co.rsk.trie.TrieKeySlice;

import static co.rsk.storagerent.RentTrackingElement.OperationType.WRITE_OPERATION;
import static co.rsk.storagerent.StorageRentComputation.*;

public class RentTrackingElement {

    public enum OperationType { WRITE_OPERATION, READ_OPERATION }

    private final TrieKeySlice key;
    private final OperationType operationType;
    private final boolean loadsContractCode;

    private Long nodeSize;
    private Long lastRentPaidTimestamp;

    public RentTrackingElement(TrieKeySlice key, OperationType operationType, boolean loadsContractCode) {
        this.key = key;
        this.operationType = operationType;
        this.loadsContractCode = loadsContractCode;
    }

    public long payableRent() {
        return computeRent(
                rentDue(getNodeSize(), getLastRentPaidTimestamp()),
                rentCap(loadsContractCode),
                rentThreshold(loadsContractCode));
    }

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

    public void setNodeSize(long nodeSize) {
        this.nodeSize = nodeSize;
    }

    public void setLastRentPaidTimestamp(long lastRentPaidTimestamp) {
        this.lastRentPaidTimestamp = lastRentPaidTimestamp;
    }

    private long duration(long currentBlockTimsetamp) {
        // todo(fedejinich) is this the right way to calculate the duration or should I do it with the current system time?
        //   is it safe to do it the current system time?
        return currentBlockTimsetamp - this.lastRentPaidTimestamp;
    }

    private long rentThreshold(boolean loadsContractCode) {
        return operationType == WRITE_OPERATION ? WRITE_THRESHOLD
                : (loadsContractCode ? READ_THRESHOLD_CONTRACT_CODE : READ_THRESHOLD);
    }

    private long rentCap(boolean loadsContract) {
        return loadsContract ? RENT_CAP_CONTRACT_CODE : RENT_CAP;
    }
}
