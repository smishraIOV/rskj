package co.rsk.storagerent;

import co.rsk.test.World;
import co.rsk.test.dsl.DslProcessorException;
import org.apache.commons.lang3.NotImplementedException;
import org.ethereum.core.TransactionExecutor;
import org.junit.Test;

import java.io.FileNotFoundException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * This is the grey box testing of the StorageRent feature (RSKIP240)
 * */
public class StorageRentDSLTests {

    /**
     * Executes a simple value transfer,
     * it shouldn't trigger storage rent.
     * */
    @Test
    public void valueTransfer() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/storagerent.txt");

        String txHash1 = world.getTransactionByName("tx01")
                .getHash()
                .toHexString();

        TransactionExecutor transactionExecutor = world.getTransactionExecutor(txHash1);
        StorageRentManager storageRentManager = transactionExecutor.getStorageRentManager();

        assertFalse(transactionExecutor.isStorageRentEnabled());
        assertEquals(0, storageRentManager.getRentedNodes().size());
    }

    /**
     * Transfers tokens from an ERC20 contract
     *
     * It should
     * - Pay storage rent
     * */
    @Test
    public void tokenTransfer() {
        throw new NotImplementedException("needs to be implemented");
    }

    /**
     * Executes a transaction with an internal transaction that is going to fail, but the main transaction succeeds
     *
     * It should
     * - Pay storage rent of the main transaction +
     * - 25% of the internal transaction
     *
     * todo(fedejinich) check that internal transactions nodes are excluded from the real storage rent manager
     * */
    @Test
    public void internalTransactionFailsButOverallEndsOk() {
        throw new NotImplementedException("needs to be implemented");
    }

    /**
     * Executes a transaction with nested internal transactions, the last one fails and makes the whole execution fail
     *
     * It should
     * - Pay the 25% of storage rent for each internal transaction +
     * - Pay 25% of storage rent of the main transaction
     * */
    @Test
    public void internalTransactionUnhandledFail() {
        throw new NotImplementedException("needs to be implemented");
    }

    /**
     * Executes a transaction with nested internal transactions, they all end up ok but the main transaction fails
     *
     * It should pay 25% of the storage rent
     * */
    @Test
    public void internalTransactionsSucceedsButOverallFails() {
        throw new NotImplementedException("needs to be implemented");
    }

    /**
     * Executes a transaction with nested internal transactions, they all end up ok and the main transactions succeeds
     *
     * It should
     * - Pay storage rent for each internal transaction +
     * - Pay storage rent for the main transaction
     * */
    @Test
    public void internalTransactionsAndOverallSucceeds() {
        throw new NotImplementedException("needs to be implemented");
    }
}
