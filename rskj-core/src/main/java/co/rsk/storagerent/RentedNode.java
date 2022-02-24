package co.rsk.storagerent;

import co.rsk.trie.Trie;
import co.rsk.util.HexUtils;
import com.google.common.annotations.VisibleForTesting;
import org.ethereum.db.TrackedNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static co.rsk.storagerent.StorageRentComputation.*;
import static org.ethereum.db.OperationType.READ_OPERATION;
import static org.ethereum.db.OperationType.WRITE_OPERATION;

/**
 * A RentedNode contains the relevant data of an involved node during transaction execution.
 * It also returns the relevant information to pay the storage rent.
 * */
public class RentedNode extends TrackedNode {
    private static final Logger LOGGER_FEDE = LoggerFactory.getLogger("fede");

    private Long nodeSize;
    private Long lastRentPaidTimestamp;

    // todo(fedejinich) WRITE OPERATION & LOADS CONTRACT CODE, IS THAT AN ILLEGAL STATE?
    public RentedNode(TrackedNode trackedNode) {
        super(trackedNode.getKey(), trackedNode.getOperationType(), trackedNode.isLoadsContractCode());
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

    /**
     * Determines if a node should be replaced by another one due to different operation types,
     * the operation with the lowest threshold it's the one that leads the storage rent payment
     * */
    public boolean shouldBeReplaced(RentedNode anotherNode) {
        return operationType.equals(READ_OPERATION) &&
                anotherNode.operationType.equals(WRITE_OPERATION);
    }

    private long getNodeSize() {
        if(nodeSize == null) {
            throw new IllegalStateException("no nodeSize, initialize it by fetching storage rent data with the RentManager");
        }
        return nodeSize;
    }

    @VisibleForTesting
    public long getLastRentPaidTimestamp() {
        if(lastRentPaidTimestamp == null) {
            throw new IllegalStateException("no lastRentPaidTimestamp, initialize it by fetching storage rent data with the RentManager");
        }
        return lastRentPaidTimestamp;
    }

    private long duration(long currentBlockTimsetamp) {
        long duration = 0;
        if(this.getLastRentPaidTimestamp() == Trie.NO_RENT_TIMESTAMP) {
            LOGGER_FEDE.error("{} is a new node, it has no duration", this.printableKey(this.getKey().getData())); // todo(fedejinich) debug
            return duration; // todo(fedejinich) what is the duration for new nodes or old nodes (< Hop)?
        }

        // todo(fedejinich) is this the right way to calculate the duration?
        // todo(fedejinch) checkout oveflows
        duration = currentBlockTimsetamp - this.getLastRentPaidTimestamp();
        logDuration(currentBlockTimsetamp, duration); // todo(fedejinich) debug
        return duration;
    }

    private void logDuration(long currentBlockTimsetamp, long duration) {
        LOGGER_FEDE.error("node: {}, blockTimestamp: {}, lastRentPaidTimestamp: {} => duration: {}",
                printableKey(getKey().getData()), currentBlockTimsetamp, getLastRentPaidTimestamp(), duration);
    }

    private long rentThreshold(boolean loadsContractCode) {
        return operationType == WRITE_OPERATION ? WRITE_THRESHOLD
                : (loadsContractCode ? READ_THRESHOLD_CONTRACT_CODE : READ_THRESHOLD);
    }

    private long rentCap(boolean loadsContract) {
        return loadsContract ? RENT_CAP_CONTRACT_CODE : RENT_CAP;
    }

    public long rollbackFee(long executionBlockTimestamp) {
        return (long) (payableRent(executionBlockTimestamp) * 0.25); // todo(fedejinich) avoid casting?
    }

    // todo(fedejinich) should I override equals & hashcode?

    private String printableKey(byte[] key) {
        String s = HexUtils.toJsonHex(key);
        return s.substring(s.length() - 5);
    }
}
