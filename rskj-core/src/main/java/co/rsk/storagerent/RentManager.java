package co.rsk.storagerent;

import co.rsk.trie.Trie;
import co.rsk.trie.TrieKeySlice;
import com.google.common.annotations.VisibleForTesting;

import java.util.*;

public class RentManager {
    private final Set<Trie> trackedNodes = new HashSet<>();
    private final List<TrieKeySlice> mismatches = new ArrayList<>();

    /**
     * Tracks nodes from a Trie instance
     * todo(fedejinch) currently it's tracking all the nodes (including the intermediate ones), but that's not what we want,
     *   we just want to track the value-containg nodes involved in tx execution
     * */
    public void trackNodes(TrieKeySlice trieKeySlice, Trie trie) {
        if(trie != null) {
            List<Trie> nodes = trie.findNodes(trieKeySlice);
            if(nodes != null) {
                this.trackedNodes.addAll(nodes);
            } else {
                // track the mismatched key if is not present in trie
                mismatches.add(trieKeySlice);
            }
        }
    }

    @VisibleForTesting
    public Set<Trie> getTrackedNodes() {
        return Collections.unmodifiableSet(trackedNodes);
    }

    @VisibleForTesting
    public List<TrieKeySlice> getMismatches() {
        return Collections.unmodifiableList(mismatches);
    }
}
