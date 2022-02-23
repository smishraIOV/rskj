package co.rsk.storagerent;

import co.rsk.db.MutableTrieImpl;
import co.rsk.trie.Trie;
import org.ethereum.db.ByteArrayWrapper;
import org.ethereum.db.MutableRepository;
import org.ethereum.db.TrackedNode;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static org.ethereum.db.OperationType.READ_OPERATION;
import static org.ethereum.db.OperationType.WRITE_OPERATION;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class MutableRepositoryTrackNodesTest {

    private static final boolean LOADS_CONTRACT = false;
    public static final long CURRENT_BLOCK_TIMESTAMP = new GregorianCalendar(2022, 1, 30)
            .getTime()
            .getTime();

    private MutableRepository repository;
    private List<RentedNode> testNodes;
    private Map<ByteArrayWrapper, RentedNode> valueContainingNodes;

    @Before
    public void setup() {
        valueContainingNodes = spy(new HashMap<>());
        repository = new MutableRepository(new MutableTrieImpl(null, new Trie()));
        testNodes = testNodes();
    }
    
//    @Test
//    public void trackNode_shouldAddNodesToTheList() {
//        trackValueContainingNodes(repository, testNodes());
//
//        // all new nodes, they should be tracked normally
//        assertEquals(6, repository.storageRentNodes().size());
//        for(int i = 0; i < repository.storageRentNodes().size(); i++ ) {
//            ByteArrayWrapper key = new ByteArrayWrapper(("key" + (i + 1)).getBytes(StandardCharsets.UTF_8));
//            assertEquals(new RentedNode(new TrackedNode(key, READ_OPERATION, LOADS_CONTRACT),
//                    repository.storageRentNodes().get(key)));
//        }
//    }

    @Test
    public void trackNode_shouldTrackNodesWithTheLowestThreshold() {
        // add 6 read-operation-nodes
        trackValueContainingNodes(repository, testNodes());

        // track new write-operation-nodes, some of them are already tracked nodes but with different operation type,
        // should replace key3, key5 and add key7
        trackValueContainingNodes(repository, Arrays.asList(
                rentedNode("key3"),
                rentedNode("key5"),
                rentedNode("key7")
            )
        );

        assertEquals(7, repository.getStorageRentNodes().size());

        List<RentedNode> writeNodes = repository.getStorageRentNodes()
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

        List<RentedNode> readNodes = repository.getStorageRentNodes()
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

    private RentedNode rentedNode(String key3) {
        return new RentedNode(
            new TrackedNode(
                new ByteArrayWrapper(key3.getBytes(StandardCharsets.UTF_8)),
                WRITE_OPERATION,
                LOADS_CONTRACT)
        );
    }


//    @Test
//    public void payStorageRent_shouldPayRent() {
//        Repository repository = spy(Repository.class);
//        mockStorageRentData(repository, testNodes);
//
//        trackValueContainingNodes(this.repository, testNodes);
//
//        // it's just ONE day without paying storage rent
//        long gasLeftover = this.repository.payStorageRent(100000, CURRENT_BLOCK_TIMESTAMP, repository);
//
//        verify(repository, times(testNodes.size())).updateLastRentPaidTimestamp(any(), anyLong());
//        assertEquals(70000, gasLeftover); // every node is paying the rent cap
//    }
//
//    @Test
//    public void payStorageRent_shouldThrowOOGException() {
//        Repository repository = spy(Repository.class);
//        mockStorageRentData(repository, testNodes);
//
//        trackValueContainingNodes(this.repository, testNodes);
//
//        int notEnoughGas = 29999;
//
//        try{
//            this.repository.payStorageRent(notEnoughGas, CURRENT_BLOCK_TIMESTAMP, repository);
//        } catch (Program.OutOfGasException e) {
//            String expectedMessage = String.format(
//                    "not enough gas to pay storage rent. gas: {}, gasNeeded: {}",
//                    notEnoughGas, 30000);
//            assertEquals(expectedMessage, e.getMessage());
//        }
//    }
//
//    private void mockStorageRentData(Repository repository, List<RentedNode> testNodes) {
//        testNodes.forEach(t -> {
//            Map<String, Long> storageRentData = new HashMap<>();
//            storageRentData.put("nodeSize", 32L); // maximum storage cell size
//            storageRentData.put("lastRentPaidTimestamp",
//                    new GregorianCalendar(2022, 1, 29).getTime().getTime());
//
//            when(repository.fetchStorageRentData(t.getKey().getData())).thenReturn(storageRentData);
//        });
//    }

//    @Test
//    public void trackMismatch_shouldTrackNodesWithoutDuplicates() {
//        storageRentManager.trackMismatch("key1".getBytes(StandardCharsets.UTF_8));
//        storageRentManager.trackMismatch("key2".getBytes(StandardCharsets.UTF_8));
//        storageRentManager.trackMismatch("key3".getBytes(StandardCharsets.UTF_8));
//        storageRentManager.trackMismatch("key1".getBytes(StandardCharsets.UTF_8));
//        storageRentManager.trackMismatch("key3".getBytes(StandardCharsets.UTF_8));
//
//        assertEquals(3, storageRentManager.getMismatches().size());
//    }

    private void trackValueContainingNodes(MutableRepository repository, List<RentedNode> nodes) {
        nodes.forEach(v -> {
            repository.trackNode(v.getKey().getData(), v.getOperationType());
            verify(valueContainingNodes).put(v.getKey(), v);
        });
    }

    private List<RentedNode> testNodes() {
        return Arrays.asList(
                rentedNode("key1"),
                rentedNode("key2"),
                rentedNode("key3"),
                rentedNode("key4"),
                rentedNode("key5"),
                rentedNode("key6")
        );
    }
}
