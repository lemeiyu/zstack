package org.zstack.header.storage.primary;

/**
 * Created by frank on 6/9/2015.
 */
public class VolumeSnapshotCapability {
    public boolean isSupportCreateOnHypervisor() {
        return supportCreateOnHypervisor;
    }

    public void setSupportCreateOnHypervisor(boolean supportCreateOnHypervisor) {
        this.supportCreateOnHypervisor = supportCreateOnHypervisor;
    }

    public static enum VolumeSnapshotArrangementType {
        CHAIN,
        INDIVIDUAL
    }

    private boolean support;
    private boolean supportCreateOnHypervisor;
    private VolumeSnapshotArrangementType arrangementType;

    public boolean isSupport() {
        return support;
    }

    public void setSupport(boolean support) {
        this.support = support;
    }

    public VolumeSnapshotArrangementType getArrangementType() {
        return arrangementType;
    }

    public void setArrangementType(VolumeSnapshotArrangementType arrangementType) {
        this.arrangementType = arrangementType;
    }
}
