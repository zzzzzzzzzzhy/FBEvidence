package com.evidence.contracts;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.fisco.bcos.sdk.abi.TypeReference;
import org.fisco.bcos.sdk.abi.datatypes.Function;
import org.fisco.bcos.sdk.abi.datatypes.Type;
import org.fisco.bcos.sdk.abi.datatypes.Utf8String;
import org.fisco.bcos.sdk.abi.datatypes.generated.Uint256;
import org.fisco.bcos.sdk.abi.datatypes.Bool;
import org.fisco.bcos.sdk.client.Client;
import org.fisco.bcos.sdk.contract.Contract;
import org.fisco.bcos.sdk.crypto.CryptoSuite;
import org.fisco.bcos.sdk.crypto.keypair.CryptoKeyPair;
import org.fisco.bcos.sdk.model.CryptoType;
import org.fisco.bcos.sdk.model.TransactionReceipt;
import org.fisco.bcos.sdk.model.callback.TransactionCallback;
import org.fisco.bcos.sdk.transaction.model.exception.ContractException;

/**
 * 存证智能合约Java包装类
 * 注意：这是一个示例合约，实际使用时需要根据具体的Solidity合约生成
 */
@SuppressWarnings("unchecked")
public class EvidenceContract extends Contract {

    // 对于已部署的合约，load操作不需要BINARY字节码
    public static final String[] BINARY_ARRAY = {""};

    public static final String BINARY = String.join("", BINARY_ARRAY);

    public static final String[] ABI_ARRAY = {
            "[{\"constant\":false,\"inputs\":[{\"name\":\"_fileHash\",\"type\":\"string\"},{\"name\":\"_fileName\",\"type\":\"string\"},{\"name\":\"_uploader\",\"type\":\"string\"},{\"name\":\"_fileSize\",\"type\":\"uint256\"},{\"name\":\"_description\",\"type\":\"string\"}],\"name\":\"addEvidence\",\"outputs\":[{\"name\":\"success\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"_fileHash\",\"type\":\"string\"}],\"name\":\"getEvidenceByHash\",\"outputs\":[{\"name\":\"fileHash\",\"type\":\"string\"},{\"name\":\"fileName\",\"type\":\"string\"},{\"name\":\"uploader\",\"type\":\"string\"},{\"name\":\"fileSize\",\"type\":\"uint256\"},{\"name\":\"description\",\"type\":\"string\"},{\"name\":\"timestamp\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"_fileHash\",\"type\":\"string\"}],\"name\":\"verifyEvidence\",\"outputs\":[{\"name\":\"exists\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"_uploader\",\"type\":\"string\"}],\"name\":\"getEvidencesByUploader\",\"outputs\":[{\"name\":\"hashes\",\"type\":\"string[]\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"getEvidenceCount\",\"outputs\":[{\"name\":\"count\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[{\"name\":\"_offset\",\"type\":\"uint256\"},{\"name\":\"_limit\",\"type\":\"uint256\"}],\"name\":\"getEvidenceList\",\"outputs\":[{\"name\":\"hashes\",\"type\":\"string[]\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"_fileHash\",\"type\":\"string\"},{\"name\":\"_description\",\"type\":\"string\"}],\"name\":\"updateEvidenceDescription\",\"outputs\":[{\"name\":\"success\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"getContractInfo\",\"outputs\":[{\"name\":\"contractOwner\",\"type\":\"address\"},{\"name\":\"totalEvidences\",\"type\":\"uint256\"},{\"name\":\"contractBalance\",\"type\":\"uint256\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"owner\",\"outputs\":[{\"name\":\"\",\"type\":\"address\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":true,\"inputs\":[],\"name\":\"stopped\",\"outputs\":[{\"name\":\"\",\"type\":\"bool\"}],\"payable\":false,\"stateMutability\":\"view\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[],\"name\":\"emergencyStop\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[],\"name\":\"restart\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[{\"name\":\"_newOwner\",\"type\":\"address\"}],\"name\":\"transferOwnership\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"constant\":false,\"inputs\":[],\"name\":\"destroy\",\"outputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"function\"},{\"inputs\":[],\"payable\":false,\"stateMutability\":\"nonpayable\",\"type\":\"constructor\"},{\"payable\":true,\"stateMutability\":\"payable\",\"type\":\"fallback\"},{\"anonymous\":false,\"inputs\":[{\"indexed\":false,\"name\":\"fileHash\",\"type\":\"string\"},{\"indexed\":false,\"name\":\"fileName\",\"type\":\"string\"},{\"indexed\":false,\"name\":\"uploader\",\"type\":\"string\"},{\"indexed\":false,\"name\":\"fileSize\",\"type\":\"uint256\"},{\"indexed\":false,\"name\":\"timestamp\",\"type\":\"uint256\"}],\"name\":\"EvidenceAdded\",\"type\":\"event\"},{\"anonymous\":false,\"inputs\":[{\"indexed\":false,\"name\":\"fileHash\",\"type\":\"string\"},{\"indexed\":true,\"name\":\"verifier\",\"type\":\"address\"},{\"indexed\":false,\"name\":\"result\",\"type\":\"bool\"}],\"name\":\"EvidenceVerified\",\"type\":\"event\"}]"
    };

    public static final String ABI = String.join("", ABI_ARRAY);

    public static final String FUNC_ADDEVIDENCE = "addEvidence";
    public static final String FUNC_GETEVIDENCEBYHASH = "getEvidenceByHash";
    public static final String FUNC_VERIFYEVIDENCE = "verifyEvidence";
    public static final String FUNC_GETEVIDENCESBYUPLOADER = "getEvidencesByUploader";
    public static final String FUNC_GETEVIDENCECOUNT = "getEvidenceCount";
    public static final String FUNC_GETEVIDENCELIST = "getEvidenceList";
    public static final String FUNC_UPDATEEVIDENCEDESCRIPTION = "updateEvidenceDescription";
    public static final String FUNC_GETCONTRACTINFO = "getContractInfo";
    public static final String FUNC_OWNER = "owner";
    public static final String FUNC_STOPPED = "stopped";
    public static final String FUNC_EMERGENCYSTOP = "emergencyStop";
    public static final String FUNC_RESTART = "restart";
    public static final String FUNC_TRANSFEROWNERSHIP = "transferOwnership";
    public static final String FUNC_DESTROY = "destroy";

    protected EvidenceContract(String contractAddress, Client client, CryptoKeyPair credential) {
        super(getBinary(client.getCryptoSuite()), contractAddress, client, credential);
    }

    public static String getBinary(CryptoSuite cryptoSuite) {
        return (cryptoSuite.getCryptoTypeConfig() == CryptoType.ECDSA_TYPE ? BINARY : BINARY);
    }

    public static EvidenceContract load(String contractAddress, Client client, CryptoKeyPair credential) {
        return new EvidenceContract(contractAddress, client, credential);
    }

    public static EvidenceContract deploy(Client client, CryptoKeyPair credential) throws ContractException {
        return deploy(EvidenceContract.class,
                client,
                credential,
                getBinary(client.getCryptoSuite()),
                "");
    }

    public TransactionReceipt addEvidence(String _fileHash, String _fileName, String _uploader,
                                          String _fileSize, String _description) {
        final Function function = new Function(
                FUNC_ADDEVIDENCE,
                Arrays.<Type>asList(new Utf8String(_fileHash),
                        new Utf8String(_fileName),
                        new Utf8String(_uploader),
                        new Uint256(new BigInteger(_fileSize)),
                        new Utf8String(_description)),
                Collections.<TypeReference<?>>emptyList());
        return executeTransaction(function);
    }

    public List<Type> getEvidenceByHash(String _fileHash) throws ContractException {
        final Function function = new Function(FUNC_GETEVIDENCEBYHASH,
                Arrays.<Type>asList(new Utf8String(_fileHash)),
                Arrays.<TypeReference<?>>asList(new TypeReference<Utf8String>() {},
                        new TypeReference<Utf8String>() {},
                        new TypeReference<Utf8String>() {},
                        new TypeReference<Uint256>() {},
                        new TypeReference<Utf8String>() {},
                        new TypeReference<Uint256>() {}));
        return executeCallWithMultipleValueReturn(function);
    }

    public Boolean verifyEvidence(String _fileHash) throws ContractException {
        final Function function = new Function(FUNC_VERIFYEVIDENCE,
                Arrays.<Type>asList(new Utf8String(_fileHash)),
                Arrays.<TypeReference<?>>asList(new TypeReference<Bool>() {}));
        List<Type> result = executeCallWithMultipleValueReturn(function);
        return (Boolean) result.get(0).getValue();
    }

    public static String getABI() {
        return ABI;
    }
}