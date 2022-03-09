package co.rsk.storagerent;

import co.rsk.test.World;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.util.HexUtils;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionExecutor;
import org.ethereum.util.ByteUtil;
import org.junit.Ignore;
import org.junit.Test;

import java.io.FileNotFoundException;

import static co.rsk.storagerent.StorageRentManager.NO_RENT_TO_PAY;
import static org.junit.Assert.*;

/**
 * This is the grey box testing of the StorageRent feature (RSKIP240)
 * */
public class StorageRentDSLTests {

    /**
     * Executes a simple value transfer (no data) with gaslimit > 21k (100K)
     * it should trigger storage rent (including the case for sending coins to contracts that permit value sends).
     * */
    @Ignore
    @Test
    public void valueTransfer() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/storagerent/value_transfer.txt");

        TransactionExecutor transactionExecutor = world.getTransactionExecutor("tx01");
        assertTrue(transactionExecutor.isStorageRentEnabled());
        assertNotEquals(NO_RENT_TO_PAY, transactionExecutor.getStorageRentManager().getPaidRent());
    }

    /**
     * Executes a simple value transfer, with exactly 21K gas and no data
     * it should not trigger storage rent.
     * todo(shree) add variations for simple sends to contracts
     * */
    @Ignore
    @Test
    public void valueTransfer_21KgasNodata() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/storagerent/value_transfer_21kgas.txt");

        TransactionExecutor transactionExecutor = world.getTransactionExecutor("tx01");
        // todo(shree): Okay, but why? Because RSKIP is not active(*), should add cases for no data && gaslimit = 21000
        assertFalse(transactionExecutor.isStorageRentEnabled());
        assertEquals(NO_RENT_TO_PAY, transactionExecutor.getStorageRentManager().getPaidRent());
    }

    /**
     * Transfers tokens from an ERC20 contract
     *
     * It should
     * - Pay storage rent
     * */
    //@Ignore
    @Test
    public void tokenTransfer() throws FileNotFoundException, DslProcessorException { // todo(fedejinich) currently StorageRent payment it's disabled
        World world = World.processedWorld("dsl/storagerent/token_transfer.txt");
        // todo(shree) some rent timestamps are getting updated after each tx, even without payment. Check logic used for testing.
        /**
         * check token transfer
         * */

        byte[] acc1Address = world.getAccountByName("acc1")
                .getAddress()
                .getBytes();
        byte[] acc2Address = world.getAccountByName("acc2")
                .getAddress()
                .getBytes();

        // checks the balanceOf acc1 BEFORE doing the token transfer
        assertEquals(1000, balanceOf(world, acc1Address, "tx03"));
        // check the balanceOf of acc2 BEFORE doing the token transfer
        assertEquals(0, balanceOf(world,acc2Address, "tx04"));
        // checks the balanceOf acc1 AFTER doing the token transfer
        assertEquals(900, balanceOf(world, acc1Address,"tx06"));
        // checks the balanceOf acc2 AFTER doing the token transfer
        assertEquals(100, balanceOf(world, acc2Address,"tx07"));

        /**
         * storage rent checks
         * */

        // todo(fedejinich) need to accumulate rent to check real payments

        long rentedNodesBalanceOfAcc1 = 3; //todo(shree): why are these different
        long rentedNodesBalanceOfAcc2 = 2; // because there is not storage cell for acc2 yet (0 balance)

        // balanceOf
        // todo(shree): these are token balance checks, yeah? 
        //  so the number of nodes touched should be 5 each?: sender, contract account, code, storage root, storage cell
        checkStorageRent(world,"tx03", 0, 0,
                rentedNodesBalanceOfAcc1, 0, 0);
        checkStorageRent(world,"tx04", 0, 0,
                rentedNodesBalanceOfAcc2, 0, 0);

        // transfer
        checkStorageRent(world,"tx05", 0, 0, 4, 0, 0);

        // Now update the number of nodes touched (since there is a storage cell for acc2 token bal)        
        rentedNodesBalanceOfAcc2 = 3; // because there is not storage cell for acc2 yet (0 balance)        
        // balanceOf
        checkStorageRent(world,"tx06", 0, 0,
                rentedNodesBalanceOfAcc1, 0, 0);
        checkStorageRent(world,"tx07", 0, 0,
                rentedNodesBalanceOfAcc2, 0, 0); // todo(shree) this was off by 1
    }

    /**
     * Contract deploy
     * */
    @Ignore
    @Test
    public void contractDeploy() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/storagerent/contract_deploy.txt");

        // check contract deploy
        TransactionExecutor transactionExecutor = world.getTransactionExecutor("tx01");

        assertTrue(transactionExecutor.isStorageRentEnabled());
        checkStorageRent(world, "tx01", 0, 0, 8, 0, 0);
    }

    /**
     * Executes a transaction with an internal transaction that is going to fail, but the main transaction succeeds
     *
     * It should
     * - Pay storage rent of the main transaction +
     * - 25% of the internal transaction
     *
     * todo(fedejinich) check that internal transactions nodes are excluded from the real storage rent manager
     *   1. set a storage cell to a value and update timestamp
     *   2. try to set the same storage cell to another value, this time it's going to fail
     *   3. rented nodes from 1 & 2 should be the same but one transaction should exclude them from storage rent payment & charge 25%
     *      todo(shree): suppose a node is added to tracked nodes (will be updated for sure), if touched again and reverted (internal tx), should not be charged 25%!
                .. this happens because there is no seen before list/cache in this implementation. 
     * */
    @Ignore
    @Test
    public void internalTransactionFailsButOverallEndsOk() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/storagerent/nested_call_handled_fail.txt");

//      checkStorageRent(world, "", 1, 1, 1);
        // todo(fedejinich) check that contract C doesn't commits state changes and doesn't updates its timestamp
    }

    /**
     * Executes a transaction with nested internal transactions, the last one fails (unhandled) and makes the whole execution fail
     *
     * It should
     * - Pay the 25% of storage rent for each internal transaction +
     * - Pay 25% of storage rent of the main transaction
     * */
    @Ignore
    @Test
    public void internalTransactionUnhandledFail() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/storagerent/nested_call_unhandled_fail.txt");
        // todo(fedejinich) currently failing because revert() clears rentedNodes list
        //   still need to support reverts
    }

    /**
     * Executes a transaction with nested internal transactions, they all end up ok but the main transaction fails
     *
     * It should pay 25% of the storage rent
     * */
    @Ignore
    @Test
    public void internalTransactionsSucceedsButOverallFails() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/storagerent/nested_call_succeeds_overall_fail.txt");
        // todo(fedejinich) currently failing because revert() clears rentedNodes list
        //   still need to support reverts
    }

    /**
     * Executes a transaction with nested internal transactions, they all end up ok and the main transactions succeeds
     *
     * It should
     * - Pay storage rent for each internal transaction +
     * - Pay storage rent for the main transaction
     * */
    @Ignore
    @Test
    public void internalTransactionsAndOverallSucceeds() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/storagerent/nested_call_succeeds_overall_succeeds.txt");


        checkStorageRent(world, "tx04", 1, 0, 2, 0, 1);
    }

    /**
     * Returns the token balance of an account given a txName.
     * @param world a world
     * @param address address to check balanceOf
     * @param balanceOfTransaction a transaction name corresponding to a "balanceOf" transaction
     * */
    private int balanceOf(World world, byte[] address, String balanceOfTransaction) {
        Transaction transactionByName = world.getTransactionByName(balanceOfTransaction);

        String balanceOf = "70a08231"; // keccak("balanceOf(address)")

        // UNFORMATED DATA is encoded by two hex digits per byte
        String padding = "000000000000000000000000"; // padding.size = 24 (12 * 2)
        String addressString = HexUtils.toJsonHex(address).substring(2); // address.size = 40 (20 * 2)

        String data = HexUtils.toJsonHex(transactionByName.getData()).substring(2);

        // check querying the right address. UNFORMATTED DATA
        assertEquals(balanceOf + padding + addressString, data);

        // get balance
        byte[] accountBalance = world.getTransactionExecutor(balanceOfTransaction)
                .getResult()
                .getHReturn();

        return ByteUtil.byteArrayToInt(accountBalance);
    }

    // todo(shree) Example from contract_deploy test: checkStorageRent(world, "tx01", 1, 0, 8, 0, 0);
    private void checkStorageRent(World world, String txName, long paidRent, long rollbackRent, long rentedNodesCount,
                                  long rollbackNodesCount, long mismatches) {
        TransactionExecutor transactionExecutor = world.getTransactionExecutor(txName);
        StorageRentManager storageRentManager = transactionExecutor.getStorageRentManager();

        assertTrue(transactionExecutor.isStorageRentEnabled());
        // todo(shree) removed mismatches? reintroduce, even if business logic is not finalized
        //assertEquals(mismatches, storageRentManager.getMismatches().size());
        System.out.println("rentedNodesCount expected: " + rentedNodesCount + " and actual: " + storageRentManager.getRentedNodes().size());
        assertEquals(rentedNodesCount, (long) storageRentManager.getRentedNodes().size());
        //System.out.println("rentedNodesCount expected: " + rentedNodesCount + " and actual: " + storageRentManager.getRolledBackNodes().size());
        assertEquals(rollbackNodesCount, storageRentManager.getRolledBackNodes().size());
        assertEquals(paidRent, storageRentManager.getPaidRent());
        assertEquals(rollbackRent, storageRentManager.getRollbacksRent());
    }
}
