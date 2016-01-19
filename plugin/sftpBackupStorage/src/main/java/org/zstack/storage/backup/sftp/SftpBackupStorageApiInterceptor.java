package org.zstack.storage.backup.sftp;

import org.springframework.beans.factory.annotation.Autowired;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.SimpleQuery;
import org.zstack.core.db.SimpleQuery.Op;
import org.zstack.core.errorcode.ErrorFacade;
import org.zstack.header.errorcode.SysErrors;
import org.zstack.header.apimediator.ApiMessageInterceptionException;
import org.zstack.header.apimediator.ApiMessageInterceptor;
import org.zstack.header.message.APIMessage;
import org.zstack.header.query.QueryCondition;
import org.zstack.header.query.QueryOp;
import org.zstack.utils.network.NetworkUtils;

/**
 */
public class SftpBackupStorageApiInterceptor implements ApiMessageInterceptor {
    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private ErrorFacade errf;

    @Override
    public APIMessage intercept(APIMessage msg) throws ApiMessageInterceptionException {
        if (msg instanceof APIAddSftpBackupStorageMsg) {
            validate((APIAddSftpBackupStorageMsg)msg);
        } else if (msg instanceof APIQuerySftpBackupStorageMsg) {
            validate((APIQuerySftpBackupStorageMsg)msg);
        }

        return msg;
    }

    private void validate(APIQuerySftpBackupStorageMsg msg) {
        boolean found = false;
        for (QueryCondition qcond : msg.getConditions()) {
            if ("type".equals(qcond.getName())) {
                qcond.setOp(QueryOp.EQ.toString());
                qcond.setValue(SftpBackupStorageConstant.SFTP_BACKUP_STORAGE_TYPE);
                found = true;
                break;
            }
        }

        if (!found) {
            msg.addQueryCondition("type", QueryOp.EQ, SftpBackupStorageConstant.SFTP_BACKUP_STORAGE_TYPE);
        }
    }

    private void validate(APIAddSftpBackupStorageMsg msg) {
        if (!NetworkUtils.isIpv4Address(msg.getHostname()) && !NetworkUtils.isHostname(msg.getHostname())) {
            throw new ApiMessageInterceptionException(errf.stringToInvalidArgumentError(
                    String.format("hostname[%s] is neither an IPv4 address nor a valid hostname", msg.getHostname())
            ));
        }

        SimpleQuery<SftpBackupStorageVO> q = dbf.createQuery(SftpBackupStorageVO.class);
        q.add(SftpBackupStorageVO_.hostname, Op.EQ, msg.getHostname());
        if (q.isExists()) {
            throw new ApiMessageInterceptionException(errf.instantiateErrorCode(SysErrors.OPERATION_ERROR,
                    String.format("duplicate backup storage. There has been a sftp backup storage[hostname:%s] existing", msg.getHostname())
            ));
        }
    }
}
