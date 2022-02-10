package co.rsk.storagerent;

import org.ethereum.db.ByteArrayWrapper;

import java.util.Objects;

import static co.rsk.storagerent.StorageRentComputation.*;
import static co.rsk.storagerent.RentedNode.OperationType.READ_OPERATION;
import static co.rsk.storagerent.RentedNode.OperationType.WRITE_OPERATION;

/**
 * A RentedNode contains the relevant data of an involved node during transaction execution.
 * It also returns the relevant information to pay the storage rent.
 * */
public class RentedNode {
    public enum OperationType { WRITE_OPERATION, READ_OPERATION }

    private final ByteArrayWrapper key; // using this type because it's harder to compare raw byte arrays (byte[])
    private final OperationType operationType;

    private final boolean loadsContractCode;
    private Long nodeSize;
    private Long lastRentPaidTimestamp;

    // todo(fedejinich) WRITE OPERATION & LOADS CONTRACT CODE, IS THAT AN ILLEGAL STATE?
    public RentedNode(ByteArrayWrapper key, OperationType operationType, boolean loadsContractCode) {
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

    public void setStorageRentData(Long nodeSize, Long lastRentPaidTimestamp) {
        this.nodeSize = nodeSize;
        this.lastRentPaidTimestamp = lastRentPaidTimestamp;
    }

    public ByteArrayWrapper getKey() {
        return this.key;
    }

    /**
     * Determines if a node should be replaced by another one due to different operation types,
     * the operation with the lowest threshold it's the one that leads the storage rent payment
     * */
    public boolean shouldBeReplaced(RentedNode anotherNode) {
        return operationType.equals(READ_OPERATION) &&
                anotherNode.operationType.equals(WRITE_OPERATION);
    }

    public OperationType getOperationType() {
        return this.operationType;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RentedNode)) return false;
        RentedNode that = (RentedNode) o;
        return loadsContractCode == that.loadsContractCode &&
                getKey().equals(that.getKey()) &&
                getOperationType() == that.getOperationType() &&
                Objects.equals(nodeSize, that.nodeSize) &&
                Objects.equals(lastRentPaidTimestamp, that.lastRentPaidTimestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getKey(), getOperationType(), loadsContractCode, nodeSize, lastRentPaidTimestamp);
    }
}
