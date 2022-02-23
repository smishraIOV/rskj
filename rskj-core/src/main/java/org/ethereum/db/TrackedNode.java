package org.ethereum.db;

public class TrackedNode {
    protected final ByteArrayWrapper key;
    protected final OperationType operationType;
    protected final boolean loadsContractCode;

    public TrackedNode(ByteArrayWrapper rawKey, OperationType operationType, boolean loadsContractCode) {
        this.key = rawKey;
        this.operationType = operationType;
        this.loadsContractCode = loadsContractCode;
    }

    public ByteArrayWrapper getKey() {
        return key;
    }

    public OperationType getOperationType() {
        return operationType;
    }

    public boolean isLoadsContractCode() {
        return loadsContractCode;
    }
}
