package co.rsk.storagerent;

import org.ethereum.core.Repository;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.vm.program.Program;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static co.rsk.storagerent.RentedNode.OperationType.READ_OPERATION;
import static co.rsk.storagerent.RentedNode.OperationType.WRITE_OPERATION;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class StorageRentManagerTest {

    private static final boolean LOADS_CONTRACT = false;
    public static final long CURRENT_BLOCK_TIMESTAMP = new GregorianCalendar(2022, 1, 30)
            .getTime()
            .getTime();

    private StorageRentManager storageRentManager;
    private List<RentedNode> testNodes;
    private Map<ByteArrayWrapper, RentedNode> valueContainingNodes;

    @Before
    public void setup() {
        valueContainingNodes = spy(new HashMap<>());
        storageRentManager = new StorageRentManager(valueContainingNodes);
        testNodes = testNodes();
    }
    
    @Test
    public void trackNode_shouldAddNodesToTheList() {
        trackValueContainingNodes(storageRentManager, testNodes());

        // all new nodes, they should be tracked normally
        assertEquals(6, storageRentManager.getRentedNodes().size());
        for(int i = 0; i < storageRentManager.getRentedNodes().size(); i++ ) {
            ByteArrayWrapper key = new ByteArrayWrapper(("key" + (i + 1)).getBytes(StandardCharsets.UTF_8));
            assertEquals(new RentedNode(key, READ_OPERATION, LOADS_CONTRACT),
                    storageRentManager.getRentedNodes().get(key));
        }
    }

    @Test
    public void trackNode_shouldTrackNodesWithTheLowestThreshold() {
        // add 6 read-operation-nodes
        trackValueContainingNodes(storageRentManager, testNodes());

        // track new write-operation-nodes, some of them are already tracked nodes but with different operation type,
        // should replace key3, key5 and add key7
        trackValueContainingNodes(storageRentManager, Arrays.asList(
                new RentedNode(
                    new ByteArrayWrapper("key3".getBytes(StandardCharsets.UTF_8)),
                    WRITE_OPERATION,
                    LOADS_CONTRACT),
                new RentedNode(
                    new ByteArrayWrapper("key5".getBytes(StandardCharsets.UTF_8)),
                    WRITE_OPERATION,
                    LOADS_CONTRACT),
                new RentedNode(
                    new ByteArrayWrapper("key7".getBytes(StandardCharsets.UTF_8)),
                    WRITE_OPERATION,
                    LOADS_CONTRACT)
            )
        );

        assertEquals(7, storageRentManager.getRentedNodes().size());

        List<RentedNode> writeNodes = storageRentManager.getRentedNodes()
                .values()
                .stream()
                .filter(v -> v.getOperationType().equals(WRITE_OPERATION))
                .collect(Collectors.toList());
        assertEquals(3, writeNodes.size());

        assertEquals(new ByteArrayWrapper("key3".getBytes(StandardCharsets.UTF_8)), writeNodes.get(0).getKey());
        assertEquals(WRITE_OPERATION, writeNodes.get(0).getOperationType());
        assertEquals(new ByteArrayWrapper("key5".getBytes(StandardCharsets.UTF_8)), writeNodes.get(1).getKey());
        assertEquals(WRITE_OPERATION, writeNodes.get(1).getOperationType());
        assertEquals(new ByteArrayWrapper("key7".getBytes(StandardCharsets.UTF_8)), writeNodes.get(2).getKey());
        assertEquals(WRITE_OPERATION, writeNodes.get(2).getOperationType());

        List<RentedNode> readNodes = storageRentManager.getRentedNodes()
                .values()
                .stream()
                .filter(v -> v.getOperationType().equals(READ_OPERATION))
                .collect(Collectors.toList());
        assertEquals(4, readNodes.size());

        assertEquals(new ByteArrayWrapper("key1".getBytes(StandardCharsets.UTF_8)), readNodes.get(0).getKey());
        assertEquals(READ_OPERATION, readNodes.get(0).getOperationType());
        assertEquals(new ByteArrayWrapper("key2".getBytes(StandardCharsets.UTF_8)), readNodes.get(1).getKey());
        assertEquals(READ_OPERATION, readNodes.get(1).getOperationType());
        assertEquals(new ByteArrayWrapper("key4".getBytes(StandardCharsets.UTF_8)), readNodes.get(2).getKey());
        assertEquals(READ_OPERATION, readNodes.get(2).getOperationType());
        assertEquals(new ByteArrayWrapper("key6".getBytes(StandardCharsets.UTF_8)), readNodes.get(3).getKey());
        assertEquals(READ_OPERATION, readNodes.get(3).getOperationType());
    }


    @Test
    public void payStorageRent_shouldPayRent() {
        Repository repository = spy(Repository.class);
        mockStorageRentData(repository, testNodes);

        trackValueContainingNodes(storageRentManager, testNodes);

        // it's just ONE day without paying storage rent
        long gasLeftover = storageRentManager.payStorageRent(100000, CURRENT_BLOCK_TIMESTAMP, repository);

        verify(repository, times(testNodes.size())).updateLastRentPaidTimestamp(any(), anyLong());
        assertEquals(70000, gasLeftover); // every node is paying the rent cap
    }

    @Test
    public void payStorageRent_shouldThrowOOGException() {
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

    @Test
    public void trackMismatch_shouldTrackNodesWithoutDuplicates() {
        storageRentManager.trackMismatch("key1".getBytes(StandardCharsets.UTF_8));
        storageRentManager.trackMismatch("key2".getBytes(StandardCharsets.UTF_8));
        storageRentManager.trackMismatch("key3".getBytes(StandardCharsets.UTF_8));
        storageRentManager.trackMismatch("key1".getBytes(StandardCharsets.UTF_8));
        storageRentManager.trackMismatch("key3".getBytes(StandardCharsets.UTF_8));

        assertEquals(3, storageRentManager.getMismatches().size());
    }

    private void mockStorageRentData(Repository repository, List<RentedNode> testNodes) {
        testNodes.forEach(t -> {
            Map<String, Long> storageRentData = new HashMap<>();
            storageRentData.put("nodeSize", 32L); // maximum storage cell size
            storageRentData.put("lastRentPaidTimestamp",
                    new GregorianCalendar(2022, 1, 29).getTime().getTime());

            when(repository.fetchStorageRentData(t.getKey().getData())).thenReturn(storageRentData);
        });
    }

    private void trackValueContainingNodes(StorageRentManager storageRentManager, List<RentedNode> nodes) {
        nodes.forEach(v -> {
            storageRentManager.trackNode(v);
            verify(valueContainingNodes).put(v.getKey(), v);
        });
    }

    private List<RentedNode> testNodes() {
        return Arrays.asList(
                new RentedNode(
                        new ByteArrayWrapper("key1".getBytes(StandardCharsets.UTF_8)),
                        READ_OPERATION,
                        LOADS_CONTRACT),
                new RentedNode(
                        new ByteArrayWrapper("key2".getBytes(StandardCharsets.UTF_8)),
                        READ_OPERATION,
                        LOADS_CONTRACT),
                new RentedNode(
                        new ByteArrayWrapper("key3".getBytes(StandardCharsets.UTF_8)),
                        READ_OPERATION,
                        LOADS_CONTRACT),
                new RentedNode(
                        new ByteArrayWrapper("key4".getBytes(StandardCharsets.UTF_8)),
                        READ_OPERATION,
                        LOADS_CONTRACT),
                new RentedNode(
                        new ByteArrayWrapper("key5".getBytes(StandardCharsets.UTF_8)),
                        READ_OPERATION,
                        LOADS_CONTRACT),
                new RentedNode(
                        new ByteArrayWrapper("key6".getBytes(StandardCharsets.UTF_8)),
                        READ_OPERATION,
                        LOADS_CONTRACT)
        );
    }
}
