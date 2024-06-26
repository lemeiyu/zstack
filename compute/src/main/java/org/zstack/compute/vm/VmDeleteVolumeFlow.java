package org.zstack.compute.vm;

import org.springframework.beans.factory.annotation.Autowire;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Configurable;
import org.springframework.transaction.annotation.Transactional;
import org.zstack.core.cascade.CascadeConstant;
import org.zstack.core.cascade.CascadeFacade;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.componentloader.PluginRegistry;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.workflow.FlowChainBuilder;
import org.zstack.header.core.Completion;
import org.zstack.header.core.workflow.*;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.vm.VmInstanceConstant;
import org.zstack.header.vm.VmInstanceConstant.Params;
import org.zstack.header.vm.VmInstanceDeletionPolicyManager.VmInstanceDeletionPolicy;
import org.zstack.header.vm.VmInstanceSpec;
import org.zstack.header.volume.*;
import org.zstack.utils.CollectionUtils;
import org.zstack.utils.Utils;
import org.zstack.utils.function.Function;
import org.zstack.utils.logging.CLogger;

import javax.persistence.Query;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Configurable(preConstruction = true, autowire = Autowire.BY_TYPE)
public class VmDeleteVolumeFlow extends NoRollbackFlow {
    private static final CLogger logger = Utils.getLogger(VmDeleteVolumeFlow.class);

    @Autowired
    protected DatabaseFacade dbf;
    @Autowired
    protected CloudBus bus;
    @Autowired
    private CascadeFacade casf;
    @Autowired
    private PluginRegistry pluginRgty;

    @Override
    public void run(final FlowTrigger trigger, Map data) {
        VmInstanceSpec spec = (VmInstanceSpec) data.get(VmInstanceConstant.Params.VmInstanceSpec.toString());
        final boolean deleteDataDisk = VmGlobalConfig.DELETE_DATA_VOLUME_ON_VM_DESTROY.value(Boolean.class);

        /* data volume must be detached anyway no matter if it is going to be deleted */
        if (spec.getVmInventory().getAllVolumes().size() > 1) {
            detachDataVolumes(spec);
        }

        final VmInstanceDeletionPolicy deletionPolicy = (VmInstanceDeletionPolicy) data.get(Params.DeletionPolicy);

        if (deletionPolicy.equals(VmInstanceDeletionPolicy.KeepVolume)) {
            trigger.next();
            return;
        }

        List<String> volumeTypes = Arrays.asList(VolumeType.Root.toString(), VolumeType.Memory.toString(), VolumeType.Cache.toString());
        List<VolumeDeletionStruct> ctx = CollectionUtils.transformToList(spec.getVmInventory().getAllVolumes(), new Function<VolumeDeletionStruct, VolumeInventory>() {
            @Override
            public VolumeDeletionStruct call(VolumeInventory arg) {
                if (VolumeType.Data.toString().equals(arg.getType()) && !deleteDataDisk) {
                    return null;
                }

                VolumeDeletionStruct s = new VolumeDeletionStruct();
                s.setInventory(arg);
                if (volumeTypes.contains(arg.getType())) {
                    s.setDeletionPolicy(deletionPolicy.toString());
                }
                // for data volume, use volume's own deletion policy

                return s;
            }
        });

        final String issuer = VolumeVO.class.getSimpleName();
        FlowChain chain = FlowChainBuilder.newSimpleFlowChain();
        chain.setName("delete-volumes-in-VmDeleteVolumeFlow");
        chain.then(new NoRollbackFlow() {
            @Override
            public void run(FlowTrigger trigger1, Map data1) {
                casf.asyncCascade(CascadeConstant.DELETION_DELETE_CODE, issuer, ctx, new Completion(trigger1) {
                    @Override
                    public void success() {
                        trigger1.next();
                    }

                    @Override
                    public void fail(ErrorCode errorCode) {
                        trigger1.fail(errorCode);
                    }
                });
            }
        }).done(new FlowDoneHandler(trigger) {
            @Override
            public void handle(Map data) {
                trigger.next();
            }
        }).error(new FlowErrorHandler(trigger) {
            @Override
            public void handle(ErrorCode errCode, Map data) {
                trigger.fail(errCode);
            }
        }).start();
    }

    @Transactional
    private void detachDataVolumes(VmInstanceSpec spec) {
        List<String> dataVolumeUuids = CollectionUtils.transformToList(spec.getVmInventory().getAllVolumes(), new Function<String, VolumeInventory>() {
            @Override
            public String call(VolumeInventory arg) {
                return VolumeType.Data.toString().equals(arg.getType()) ? arg.getUuid() : null;
            }
        });

        if (dataVolumeUuids == null || dataVolumeUuids.isEmpty()) {
            return;
        }

        //NOTE(weiw): not using batch sql to avoid deadlock
        for (String volumeUuid: dataVolumeUuids) {
            String sql = "update VolumeVO vol set vol.vmInstanceUuid = null, vol.lastVmInstanceUuid = :vmUuid where vol.uuid in (:uuids)";
            Query q = dbf.getEntityManager().createQuery(sql);
            q.setParameter("uuids", volumeUuid);
            q.setParameter("vmUuid", spec.getVmInventory().getUuid());
            q.executeUpdate();
        }
    }
}
