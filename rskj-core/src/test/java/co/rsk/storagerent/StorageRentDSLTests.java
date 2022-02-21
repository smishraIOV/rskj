package co.rsk.storagerent;

import co.rsk.test.World;
import co.rsk.test.dsl.DslProcessorException;
import co.rsk.util.HexUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.ethereum.core.Transaction;
import org.ethereum.core.TransactionExecutor;
import org.ethereum.util.ByteUtil;
import org.ethereum.vm.program.ProgramResult;
import org.junit.Test;

import java.io.FileNotFoundException;

import static co.rsk.storagerent.StorageRentManager.NO_RENT_TO_PAY;
import static org.junit.Assert.*;

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
        World world = World.processedWorld("dsl/storagerent/value_transfer.txt");

        TransactionExecutor transactionExecutor = world.getTransactionExecutor("tx01");

        assertFalse(transactionExecutor.isStorageRentEnabled());
        assertEquals(NO_RENT_TO_PAY, transactionExecutor.getStorageRentManager().getPaidRent());
    }

    /**
     * Transfers tokens from an ERC20 contract
     *
     * It should
     * - Pay storage rent
     * */
    @Test
    public void tokenTransfer() throws FileNotFoundException, DslProcessorException { // todo(fedejinich) currently StorageRent payment it's disabled
        World world = World.processedWorld("dsl/storagerent/token_transfer.txt");

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

        long rentedNodesBalanceOfAcc1 = 3;
        long rentedNodesBalanceOfAcc2 = 2;

        // balanceOf
        checkStorageRent(world,"tx03", 1, rentedNodesBalanceOfAcc1, 0);
        checkStorageRent(world,"tx04", 1, rentedNodesBalanceOfAcc2, 0);

        // transfer
        checkStorageRent(world,"tx05", 1, 4, 0);

        // balanceOf
        checkStorageRent(world,"tx06", 1, rentedNodesBalanceOfAcc1, 0);
        checkStorageRent(world,"tx07", 1, rentedNodesBalanceOfAcc2, 0);
    }

    private void checkStorageRent(World world, String txName, long paidRent, long rentedNodesCount, long mismatches) {
        TransactionExecutor transactionExecutor = world.getTransactionExecutor(txName);
        StorageRentManager storageRentManager = transactionExecutor.getStorageRentManager();

        assertTrue(transactionExecutor.isStorageRentEnabled());
//        assertEquals(mismatches, storageRent.getMismatches().size());
        assertEquals(rentedNodesCount, storageRentManager.getRentedNodes().size());
        assertEquals(paidRent, storageRentManager.getPaidRent());
    }

    /**
     * Contract deploy
     * */
    @Test
    public void contractDeploy() throws FileNotFoundException, DslProcessorException {
        World world = World.processedWorld("dsl/storagerent/contract_deploy.txt");

        // check contract deploy
        TransactionExecutor transactionExecutor = world.getTransactionExecutor("tx01");

        assertTrue(transactionExecutor.isStorageRentEnabled());
        checkStorageRent(world, "tx01", 1, 8, 0);
//        assertEquals(9, result.getRentedNodes().size()); // todo(fedejinich) why?
//        assertEquals(1, result.getPaidRent());
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
     * Executes a transaction with nested internal transactions, the last one fails (unhandled) and makes the whole execution fail
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
