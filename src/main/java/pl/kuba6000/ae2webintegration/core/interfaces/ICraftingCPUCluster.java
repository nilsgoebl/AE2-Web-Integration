package pl.kuba6000.ae2webintegration.core.interfaces;

public interface ICraftingCPUCluster {

    void web$setInternalID(int id);

    boolean web$hasCustomName();

    String web$getName();

    long web$getAvailableStorage();

    long web$getUsedStorage();

    long web$getCoProcessors();

    boolean web$isBusy();

    void web$cancel();

    IAEGenericStack web$getFinalOutput();

    long web$getActiveItems(IAEKey key);

    long web$getPendingItems(IAEKey key);

    /**
     * Returns AE2's own scheduled-reason enum name for this stack, or {@code null} when it is progressing.
     */
    default String web$getScheduledReason(IAEKey key) {
        return null;
    }

    long web$getStorageItems(IAEKey key);

    void web$getAllItems(IStackList list);

    IStackList web$getWaitingFor();

}
