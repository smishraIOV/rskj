package co.rsk.storagerent;

import co.rsk.trie.TrieKeySlice;
import org.ethereum.core.Repository;
import org.ethereum.vm.program.Program;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;

import static co.rsk.storagerent.ValueContainingNode.OperationType.READ_OPERATION;
import static co.rsk.storagerent.ValueContainingNode.OperationType.WRITE_OPERATION;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class StorageRentManagerTest {

    private static final boolean LOADS_CONTRACT = false;
    public static final long CURRENT_BLOCK_TIMESTAMP = new GregorianCalendar(2022, 1, 30)
            .getTime()
            .getTime();

    private StorageRentManager storageRentManager;
    private List<ValueContainingNode> testNodes;
    private ArrayList<ValueContainingNode> valueContainingNodes;

    @Before
    public void setup() {
        valueContainingNodes = spy(new ArrayList<>());
        storageRentManager = new StorageRentManager(valueContainingNodes);
        testNodes = testNodes();
    }
    
    @Test
    public void trackNode_shouldAddNodesToTheList() {
        trackValueContainingNodes(storageRentManager, testNodes());
        assertEquals(6, storageRentManager.getValueContainingNode().size());
    }

    @Test
    public void payStorageRent_oneDayWithoutPaying_shouldPayMaximumRent() {
        Repository repository = spy(Repository.class);
        mockStorageRentData(repository, testNodes);

        trackValueContainingNodes(storageRentManager, testNodes);

        long gasLeftover = storageRentManager.payStorageRent(100000, CURRENT_BLOCK_TIMESTAMP, repository);

        verify(repository, times(testNodes.size())).updateLastRentPaidTimestamp(any(), anyLong());
        assertEquals(70000, gasLeftover);
    }

    @Test
    public void payStorageRent_notEnoughGas_shouldThrowOOGException() {
        Repository repository = spy(Repository.class);
        mockStorageRentData(repository, testNodes);

        trackValueContainingNodes(storageRentManager, testNodes);

        int notEnoughGas = 29999;

        try{
            storageRentManager.payStorageRent(notEnoughGas, CURRENT_BLOCK_TIMESTAMP, repository);
        } catch (Program.OutOfGasException e) {
            String expectedMessage = String.format(
                    "not enough gas to pay storage rent. gas: {}, gasNeeded: {}",
                    notEnoughGas, 30000);
            assertEquals(expectedMessage, e.getMessage());
        }
    }

    private void mockStorageRentData(Repository repository, List<ValueContainingNode> testNodes) {
        testNodes.forEach(t -> {
            Map<String, Long> storageRentData = new HashMap<>();
            storageRentData.put("nodeSize", 32L); // maximum storage cell size
            storageRentData.put("lastRentPaidTimestamp",
                    new GregorianCalendar(2022, 1, 29).getTime().getTime());

            when(repository.fetchStorageRentData(t.getKey().encode())).thenReturn(storageRentData);
        });
    }

    private void trackValueContainingNodes(StorageRentManager storageRentManager, List<ValueContainingNode> nodes) {
        nodes.forEach(v -> {
            storageRentManager.trackValueContainingNode(v);
            verify(valueContainingNodes).add(v);
        });
    }

    private List<ValueContainingNode> testNodes() {
        return Arrays.asList(
                new ValueContainingNode(
                        TrieKeySlice.fromKey("key1".getBytes(StandardCharsets.UTF_8)),
                        READ_OPERATION,
                        LOADS_CONTRACT),
                new ValueContainingNode(
                        TrieKeySlice.fromKey("key2".getBytes(StandardCharsets.UTF_8)),
                        READ_OPERATION,
                        LOADS_CONTRACT),
                new ValueContainingNode(
                        TrieKeySlice.fromKey("key3".getBytes(StandardCharsets.UTF_8)),
                        READ_OPERATION,
                        LOADS_CONTRACT),
                new ValueContainingNode(
                        TrieKeySlice.fromKey("key4".getBytes(StandardCharsets.UTF_8)),
                        WRITE_OPERATION,
                        LOADS_CONTRACT),
                new ValueContainingNode(
                        TrieKeySlice.fromKey("key5".getBytes(StandardCharsets.UTF_8)),
                        READ_OPERATION,
                        LOADS_CONTRACT),
                new ValueContainingNode(
                        TrieKeySlice.fromKey("key6".getBytes(StandardCharsets.UTF_8)),
                        WRITE_OPERATION,
                        LOADS_CONTRACT)
        );
    }
}
