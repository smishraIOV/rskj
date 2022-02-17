package co.rsk.storagerent;

import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.OperationType;
import org.ethereum.db.TrackedNode;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.GregorianCalendar;

import static org.ethereum.db.OperationType.READ_OPERATION;
import static org.ethereum.db.OperationType.WRITE_OPERATION;
import static org.junit.Assert.assertEquals;

/**
 * Test ValueContainingNode public methods:
 * - payableRent
 * - updatedRentTimestamp
 *
 * NOTE: rent & timestamp calculations are tested in StorageRentComputationTest
 * */
public class RentedNodeTest {

    private static final ByteArrayWrapper SOME_KEY = new ByteArrayWrapper("key".getBytes(StandardCharsets.UTF_8));
    public static final long CURRENT_BLOCK_TIMESTAMP = new GregorianCalendar(
            2022, 1, 30).getTime().getTime();
    private static final long LAST_RENT_PAID_TIMESTAMP = new GregorianCalendar(
            2022, 1, 29).getTime().getTime();
    private static final long NODE_SIZE = 10;

    @Test
    public void payableRent_readOperation() {
        checkPayableRent(READ_OPERATION,
                false,
                LAST_RENT_PAID_TIMESTAMP,
                NODE_SIZE,
                5000
        );
    }

    @Test
    public void payableRent_readOperationLoadsContractCode_shouldNotExceedCap() {
        checkPayableRent(READ_OPERATION,
                true,
                LAST_RENT_PAID_TIMESTAMP,
                NODE_SIZE,
                0
        );
    }

    @Test
    public void payableRent_writeOperation() {
        checkPayableRent(WRITE_OPERATION,
                false,
                LAST_RENT_PAID_TIMESTAMP,
                NODE_SIZE,
                5000
        );
    }

    @Test
    public void payableRent_noLastRentPaidTimestamp() {
        try{
            checkPayableRent(READ_OPERATION,
                false,
                null,
                NODE_SIZE,
                5000
            );
        } catch (IllegalStateException e) {
            assertEquals("no lastRentPaidTimestamp, initialize it by fetching storage rent data with the RentManager",
                    e.getMessage());
        }
    }

    @Test
    public void payableRent_noNodeSize() {
        try {
            checkPayableRent(READ_OPERATION,
                    false,
                    LAST_RENT_PAID_TIMESTAMP,
                    null,
                    5000
            );
        } catch (IllegalStateException e) {
            assertEquals("no nodeSize, initialize it by fetching storage rent data with the RentManager",
                    e.getMessage());
        }
    }

    private void checkPayableRent(OperationType operationType, boolean loadsContractCode,
                                  Long lastRentPaidTimestamp, Long nodeSize, int expected) {
        RentedNode rentedNode = rentedNode(SOME_KEY, operationType,
                loadsContractCode, nodeSize, lastRentPaidTimestamp);

        assertEquals(expected, rentedNode.payableRent(CURRENT_BLOCK_TIMESTAMP));
    }

    @Test
    public void name() {
    }

    @Test
    public void updatedTimestamp_readOperation() {
        checkUpdatedRentTimestamp(READ_OPERATION,
                false,
                LAST_RENT_PAID_TIMESTAMP,
                NODE_SIZE,
                1_647_152_176_000L
        );
    }

    @Test
    public void updatedTimestamp_readOperationLoadsContractCode() {
        checkUpdatedRentTimestamp(READ_OPERATION,
                true,
                LAST_RENT_PAID_TIMESTAMP,
                NODE_SIZE,
                1_646_103_600_000L
        );
    }

    @Test
    public void updatedTimestamp_writeOperation() {
        checkUpdatedRentTimestamp(WRITE_OPERATION,
                false,
                LAST_RENT_PAID_TIMESTAMP,
                NODE_SIZE,
                1_647_152_176_000L
        );
    }

    @Test
    public void updatedTimestamp_noLastRentPaidTimestamp() {
        try{
            checkUpdatedRentTimestamp(READ_OPERATION,
                    false,
                    null,
                    NODE_SIZE,
                    5000
            );
        } catch (IllegalStateException e) {
            assertEquals("no lastRentPaidTimestamp, initialize it by fetching storage rent data with the RentManager",
                    e.getMessage());
        }
    }

    @Test
    public void updatedTimestamp_noNodeSize() {
        try {
            checkUpdatedRentTimestamp(READ_OPERATION,
                    false,
                    LAST_RENT_PAID_TIMESTAMP,
                    null,
                    5000
            );
        } catch (IllegalStateException e) {
            assertEquals("no nodeSize, initialize it by fetching storage rent data with the RentManager",
                    e.getMessage());
        }
    }

    private void checkUpdatedRentTimestamp(OperationType operationType, boolean loadsContractCode,
                                           Long lastRentPaidTimestamp, Long nodeSize, long expected) {
        RentedNode rentedNode = rentedNode(SOME_KEY, operationType,
                loadsContractCode, nodeSize, lastRentPaidTimestamp);

        assertEquals(expected, rentedNode.updatedRentTimestamp(CURRENT_BLOCK_TIMESTAMP));
    }

    private RentedNode rentedNode(ByteArrayWrapper someKey, OperationType operationType,
                                  boolean loadsContractCode, Long nodeSize, Long lastRentPaidTimestamp) {
        RentedNode rentedNode = new RentedNode(
            new TrackedNode(
                someKey,
                operationType,
                loadsContractCode
            )
        );
        rentedNode.setStorageRentData(nodeSize, lastRentPaidTimestamp);
        return rentedNode;
    }
}
