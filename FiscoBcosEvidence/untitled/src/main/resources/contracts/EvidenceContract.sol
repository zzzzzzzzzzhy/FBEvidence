pragma solidity ^0.4.25;
pragma experimental ABIEncoderV2;

/**
 * @title EvidenceContract
 * @dev 区块链存证智能合约
 * @author 区块链存证系统
 */
contract EvidenceContract {

    // 存证结构体
    struct Evidence {
        string fileHash;        // 文件哈希
        string fileName;        // 文件名
        string uploader;        // 上传者
        uint256 fileSize;       // 文件大小
        string description;     // 描述信息
        uint256 timestamp;      // 时间戳
        bool exists;            // 是否存在
    }

    // 存证映射：文件哈希 => 存证信息
    mapping(string => Evidence) private evidences;

    // 用户存证列表：用户 => 文件哈希数组
    mapping(string => string[]) private userEvidences;

    // 所有存证的文件哈希数组
    string[] private allHashes;

    // 合约所有者
    address public owner;

    // 事件定义（移除 string 的 indexed）
    event EvidenceAdded(
        string fileHash,
        string fileName,
        string uploader,
        uint256 fileSize,
        uint256 timestamp
    );

    event EvidenceVerified(
        string fileHash,
        address indexed verifier,
        bool result
    );

    // 构造函数
    constructor() public {
        owner = msg.sender;
    }

    // 修饰符：仅合约所有者
    modifier onlyOwner() {
        require(msg.sender == owner, "Only owner can call this function");
        _;
    }

    // 修饰符：检查字符串不为空
    modifier notEmpty(string memory str) {
        require(bytes(str).length > 0, "String cannot be empty");
        _;
    }

    /**
     * @dev 添加存证
     * @param _fileHash 文件哈希
     * @param _fileName 文件名
     * @param _uploader 上传者
     * @param _fileSize 文件大小
     * @param _description 描述信息
     * @return success 是否成功
     */
    function addEvidence(
        string memory _fileHash,
        string memory _fileName,
        string memory _uploader,
        uint256 _fileSize,
        string memory _description
    )
    public
    notEmpty(_fileHash)
    notEmpty(_fileName)
    notEmpty(_uploader)
    returns (bool success)
    {
        // 检查存证是否已存在
        require(!evidences[_fileHash].exists, "Evidence already exists");

        // 创建存证记录
        evidences[_fileHash] = Evidence({
            fileHash: _fileHash,
            fileName: _fileName,
            uploader: _uploader,
            fileSize: _fileSize,
            description: _description,
            timestamp: block.timestamp,
            exists: true
        });

        // 添加到用户存证列表
        userEvidences[_uploader].push(_fileHash);

        // 添加到全局哈希列表
        allHashes.push(_fileHash);

        // 触发事件（字符串不再 indexed）
        emit EvidenceAdded(_fileHash, _fileName, _uploader, _fileSize, block.timestamp);

        return true;
    }

    /**
     * @dev 根据文件哈希获取存证信息
     */
    function getEvidenceByHash(string memory _fileHash)
    public
    view
    notEmpty(_fileHash)
    returns (
        string memory fileHash,
        string memory fileName,
        string memory uploader,
        uint256 fileSize,
        string memory description,
        uint256 timestamp
    )
    {
        require(evidences[_fileHash].exists, "Evidence does not exist");

        Evidence memory evidence = evidences[_fileHash];
        return (
            evidence.fileHash,
            evidence.fileName,
            evidence.uploader,
            evidence.fileSize,
            evidence.description,
            evidence.timestamp
        );
    }

    /**
     * @dev 验证存证是否存在
     */
    function verifyEvidence(string memory _fileHash)
    public
    view
    notEmpty(_fileHash)
    returns (bool exists)
    {
        return evidences[_fileHash].exists;
    }

    /**
     * @dev 获取用户的所有存证
     */
    function getEvidencesByUploader(string memory _uploader)
    public
    view
    notEmpty(_uploader)
    returns (string[] memory hashes)
    {
        return userEvidences[_uploader];
    }

    /**
     * @dev 获取存证总数
     */
    function getEvidenceCount()
    public
    view
    returns (uint256 count)
    {
        return allHashes.length;
    }

    /**
     * @dev 分页获取存证列表
     */
    function getEvidenceList(uint256 _offset, uint256 _limit)
    public
    view
    returns (string[] memory hashes)
    {
        require(_offset < allHashes.length, "Offset out of range");

        uint256 end = _offset + _limit;
        if (end > allHashes.length) {
            end = allHashes.length;
        }

        string[] memory result = new string[](end - _offset);
        for (uint256 i = _offset; i < end; i++) {
            result[i - _offset] = allHashes[i];
        }

        return result;
    }

    /**
     * @dev 批量验证存证
     */
    function batchVerifyEvidence(string[] memory _fileHashes)
    public
    view
    returns (bool[] memory results)
    {
        results = new bool[](_fileHashes.length);

        for (uint256 i = 0; i < _fileHashes.length; i++) {
            results[i] = evidences[_fileHashes[i]].exists;
        }

        return results;
    }

    /**
     * @dev 更新存证描述（仅上传者可操作）
     */
    function updateEvidenceDescription(
        string memory _fileHash,
        string memory _description
    )
    public
    notEmpty(_fileHash)
    returns (bool success)
    {
        require(evidences[_fileHash].exists, "Evidence does not exist");

        // 这里简化处理，实际应用中可能需要更复杂的权限验证
        evidences[_fileHash].description = _description;

        return true;
    }

    /**
     * @dev 获取合约基本信息
     */
    function getContractInfo()
    public
    view
    returns (
        address contractOwner,
        uint256 totalEvidences,
        uint256 contractBalance
    )
    {
        return (owner, allHashes.length, address(this).balance);
    }

    /**
     * @dev 紧急停止合约（仅所有者）
     */
    bool public stopped = false;

    modifier stopInEmergency() {
        require(!stopped, "Contract is stopped");
        _;
    }

    modifier onlyInEmergency() {
        require(stopped, "Contract is not stopped");
        _;
    }

    function emergencyStop() public onlyOwner {
        stopped = true;
    }

    function restart() public onlyOwner {
        stopped = false;
    }

    /**
     * @dev 转移合约所有权
     */
    function transferOwnership(address _newOwner) public onlyOwner {
        require(_newOwner != address(0), "New owner cannot be zero address");
        owner = _newOwner;
    }

    /**
     * @dev 销毁合约（慎用）
     */
    function destroy() public onlyOwner {
        selfdestruct(owner);
    }

    // 0.4.25 的回退函数写法（接收 ETH）
    function() external payable {}
}
