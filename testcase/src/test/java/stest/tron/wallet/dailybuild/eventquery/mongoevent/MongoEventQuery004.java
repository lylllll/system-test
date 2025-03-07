package stest.tron.wallet.dailybuild.eventquery.mongoevent;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.mongodb.BasicDBObject;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoCursor;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.bson.Document;
import org.junit.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;
import org.tron.api.WalletGrpc;
import org.tron.api.WalletSolidityGrpc;
import stest.tron.wallet.common.client.Configuration;
import stest.tron.wallet.common.client.WalletClient;
import stest.tron.wallet.common.client.utils.ByteArray;
import stest.tron.wallet.common.client.utils.ECKey;
import stest.tron.wallet.common.client.utils.HttpMethed;
import stest.tron.wallet.common.client.utils.MongoBase;
import stest.tron.wallet.common.client.utils.PublicMethed;
import stest.tron.wallet.common.client.utils.Utils;

@Slf4j
public class MongoEventQuery004 extends MongoBase {

  private final String testKey002 =
      Configuration.getByPath("testng.conf").getString("foundationAccount.key1");
  private final byte[] fromAddress = PublicMethed.getFinalAddress(testKey002);
  private final String testKey003 =
      Configuration.getByPath("testng.conf").getString("foundationAccount.key2");
  private final byte[] toAddress = PublicMethed.getFinalAddress(testKey003);
  private ManagedChannel channelFull = null;
  private WalletGrpc.WalletBlockingStub blockingStubFull = null;
  public static String httpFullNode =
      Configuration.getByPath("testng.conf").getStringList("httpnode.ip.list").get(0);
  public static String httpsolidityNode =
      Configuration.getByPath("testng.conf").getStringList("httpnode.ip.list").get(3);
  private String fullnode =
      Configuration.getByPath("testng.conf").getStringList("fullnode.ip.list").get(0);
  private String eventnode =
      Configuration.getByPath("testng.conf").getStringList("eventnode.ip.list").get(0);
  private Long maxFeeLimit =
      Configuration.getByPath("testng.conf").getLong("defaultParameter.maxFeeLimit");
  byte[] contractAddress;
  byte[] contractAddress1;
  String txid;

  private String soliditynode =
      Configuration.getByPath("testng.conf").getStringList("solidityNode.ip.list").get(0);
  private ManagedChannel channelSolidity = null;
  private WalletSolidityGrpc.WalletSolidityBlockingStub blockingStubSolidity = null;
  ECKey ecKey1 = new ECKey(Utils.getRandom());
  byte[] event001Address = ecKey1.getAddress();
  String event001Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
  private JSONObject responseContent;
  private HttpResponse response;
  String param;

  /** constructor. */
  @BeforeClass(enabled = true)
  public void beforeClass() {
    channelFull = ManagedChannelBuilder.forTarget(fullnode).usePlaintext(true).build();
    blockingStubFull = WalletGrpc.newBlockingStub(channelFull);

    channelSolidity = ManagedChannelBuilder.forTarget(soliditynode).usePlaintext(true).build();
    blockingStubSolidity = WalletSolidityGrpc.newBlockingStub(channelSolidity);

    ecKey1 = new ECKey(Utils.getRandom());
    event001Address = ecKey1.getAddress();
    event001Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
    PublicMethed.printAddress(event001Key);

    Assert.assertTrue(
        PublicMethed.sendcoin(
            event001Address, maxFeeLimit, fromAddress, testKey002, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    String filePath = "./src/test/resources/soliditycode/contractTestMongoDbEvent.sol";
    String contractName = "SimpleStorage";
    HashMap retMap = PublicMethed.getBycodeAbi(filePath, contractName);

    String code = retMap.get("byteCode").toString();
    String abi = retMap.get("abI").toString();
    contractAddress =
        PublicMethed.deployContract(
            contractName,
            abi,
            code,
            "",
            maxFeeLimit,
            0L,
            50,
            null,
            event001Key,
            event001Address,
            blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);

    String filePath2 = "src/test/resources/soliditycode/contractTestLog.sol";
    String contractName1 = "C";
    HashMap retMap2 = PublicMethed.getBycodeAbi(filePath2, contractName1);
    String code1 = retMap2.get("byteCode").toString();
    String abi1 = retMap2.get("abI").toString();
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    contractAddress1 =
        PublicMethed.deployContract(
            contractName,
            abi1,
            code1,
            "",
            maxFeeLimit,
            0L,
            50,
            null,
            event001Key,
            event001Address,
            blockingStubFull);
    PublicMethed.waitProduceNextBlock(blockingStubFull);
  }

  @Test(enabled = true, description = "MongoDB log query for contract log")
  public void test01MongoDbEventQueryForContractEvent() {
    logger.info("event001Key:" + event001Key);
    ECKey ecKey1 = new ECKey(Utils.getRandom());
    byte[] event001Address = ecKey1.getAddress();
    String event001Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
    logger.info("event001Key-001:" + event001Key);
    Assert.assertTrue(
        PublicMethed.sendcoin(
            event001Address, maxFeeLimit, fromAddress, testKey002, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    param = "700";
    txid =
        PublicMethed.triggerContract(
            contractAddress1,
            "depositForLog()",
            "#",
            false,
            0,
            maxFeeLimit,
            event001Address,
            event001Key,
            blockingStubFull);
    logger.info("txid:" + txid);
    BasicDBObject query = new BasicDBObject();
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    query.put("uniqueId", txid + "_1");
    FindIterable<Document> findIterable = mongoDatabase.getCollection("contractlog").find(query);
    MongoCursor<Document> mongoCursor = findIterable.iterator();
    Document document = null;
    int retryTimes = 40;
    while (retryTimes-- > 0) {
      PublicMethed.waitProduceNextBlock(blockingStubFull);
      if (!mongoCursor.hasNext()) {
        mongoCursor = mongoDatabase.getCollection("contractlog").find(query).iterator();
      } else {
        document = mongoCursor.next();
        break;
      }
    }
    Assert.assertTrue(retryTimes > 0);
    JSONObject jsonObject = JSON.parseObject(document.toJson());

    Assert.assertEquals(txid, jsonObject.getString("transactionId"));
    Assert.assertEquals("contractLogTrigger", jsonObject.getString("triggerName"));
    Assert.assertEquals("", jsonObject.getString("callerAddress"));
    Assert.assertNull(jsonObject.getString("logInfo"));
    Assert.assertNull("", jsonObject.getString("abi"));
    Assert.assertFalse(jsonObject.getBoolean("removed"));

    Assert.assertEquals(
        jsonObject.getJSONArray("topicList").getString(0),
        jsonObject.getJSONObject("rawData").getJSONArray("topics").getString(0));
    Assert.assertEquals(
        jsonObject.getJSONArray("topicList").getString(1),
        jsonObject.getJSONObject("rawData").getJSONArray("topics").getString(1));
    Assert.assertEquals(
        jsonObject.getJSONArray("topicList").getString(2),
        jsonObject.getJSONObject("rawData").getJSONArray("topics").getString(2));

    Assert.assertEquals(
        jsonObject.getString("data"), jsonObject.getJSONObject("rawData").getString("data"));

    expectInformationFromGetTransactionInfoById(jsonObject, txid);
    expectInformationFromGetTransactionById(jsonObject, txid);

    expectInformationFromGetBlockByNum(jsonObject, txid);
    testLatestSolidifiedBlockNumber(jsonObject);
  }

  @Test(enabled = true, description = "MongoDb log query for solidity contract log")
  public void test02MongoDbEventQueryForContractEvent() {
    logger.info("event001Key:" + event001Key);
    ECKey ecKey1 = new ECKey(Utils.getRandom());
    byte[] event001Address = ecKey1.getAddress();
    String event001Key = ByteArray.toHexString(ecKey1.getPrivKeyBytes());
    logger.info("event001Key-001:" + event001Key);
    Assert.assertTrue(
        PublicMethed.sendcoin(
            event001Address, maxFeeLimit, fromAddress, testKey002, blockingStubFull));
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    param = "700";
    txid =
        PublicMethed.triggerContract(
            contractAddress1,
            "depositForLog()",
            "#",
            false,
            0,
            maxFeeLimit,
            event001Address,
            event001Key,
            blockingStubFull);
    logger.info("txid:" + txid);
    BasicDBObject query = new BasicDBObject();
    PublicMethed.waitProduceNextBlock(blockingStubFull);
    query.put("uniqueId", txid + "_1");
    FindIterable<Document> findIterable = mongoDatabase.getCollection("soliditylog").find(query);
    MongoCursor<Document> mongoCursor = findIterable.iterator();
    Document document = null;
    int retryTimes = 40;
    while (retryTimes-- > 0) {
      PublicMethed.waitProduceNextBlock(blockingStubFull);
      if (!mongoCursor.hasNext()) {
        mongoCursor = mongoDatabase.getCollection("soliditylog").find(query).iterator();
      } else {
        document = mongoCursor.next();
        break;
      }
    }
    Assert.assertTrue(retryTimes > 0);
    JSONObject jsonObject = JSON.parseObject(document.toJson());

    Assert.assertEquals(txid, jsonObject.getString("transactionId"));
    Assert.assertEquals("solidityLogTrigger", jsonObject.getString("triggerName"));
    Assert.assertEquals("", jsonObject.getString("callerAddress"));
    Assert.assertNull(jsonObject.getString("logInfo"));
    Assert.assertNull("", jsonObject.getString("abi"));
    Assert.assertFalse(jsonObject.getBoolean("removed"));

    expectInformationFromGetTransactionInfoById(jsonObject, txid);
    expectInformationFromGetTransactionById(jsonObject, txid);
    expectInformationFromGetBlockByNum(jsonObject, txid);
    testLatestSolidifiedBlockNumber(jsonObject);
  }

  private void testLatestSolidifiedBlockNumber(JSONObject jsonObject) {
    response = HttpMethed.getNowBlockFromSolidity(httpsolidityNode);
    responseContent = HttpMethed.parseResponseContent(response);
    Long latestSolidifiedBlockNumber =
        responseContent.getJSONObject("block_header").getJSONObject("raw_data").getLong("number");
    Assert.assertTrue(
        jsonObject.getLong("latestSolidifiedBlockNumber") < latestSolidifiedBlockNumber);

    Assert.assertTrue(
        (latestSolidifiedBlockNumber - jsonObject.getLong("latestSolidifiedBlockNumber")) < 5);
  }

  private void expectInformationFromGetTransactionInfoById(JSONObject jsonObject, String txid) {
    response = HttpMethed.getTransactionInfoById(httpFullNode, txid, false);
    responseContent = HttpMethed.parseResponseContent(response);
    logger.info("timestamp:" + responseContent.getString("blockTimeStamp"));
    logger.info("timestamp:" + jsonObject.getString("timeStamp"));
    Assert.assertTrue(
        jsonObject.getString("timeStamp").contains(responseContent.getString("blockTimeStamp")));
    Assert.assertTrue(
        jsonObject.getString("blockNumber").contains(responseContent.getString("blockNumber")));
    Assert.assertEquals(
        responseContent.getJSONArray("log").getJSONObject(0).getString("address"),
        jsonObject.getJSONObject("rawData").getString("address"));

    Assert.assertEquals(
        responseContent.getJSONArray("log").getJSONObject(0).getJSONArray("topics").getString(0),
        jsonObject.getJSONObject("rawData").getJSONArray("topics").getString(0));
    Assert.assertEquals(
        responseContent.getJSONArray("log").getJSONObject(0).getJSONArray("topics").getString(1),
        jsonObject.getJSONObject("rawData").getJSONArray("topics").getString(1));
    Assert.assertEquals(
        responseContent.getJSONArray("log").getJSONObject(0).getJSONArray("topics").getString(2),
        jsonObject.getJSONObject("rawData").getJSONArray("topics").getString(2));
    Assert.assertEquals(
        responseContent.getJSONArray("log").getJSONObject(0).getString("data"),
        jsonObject.getJSONObject("rawData").getString("data"));

    logger.info("blockTimeStampFromHttp:" + responseContent.getString("blockTimeStamp"));
    logger.info("timeStampFromMongoDB:" + jsonObject.getString("timeStamp"));
  }

  private void expectInformationFromGetTransactionById(JSONObject jsonObject, String txId) {
    response = HttpMethed.getTransactionById(httpFullNode, txid);
    responseContent = HttpMethed.parseResponseContent(response);
    Assert.assertEquals(txId, jsonObject.getString("transactionId"));
    String contractAddress =
        WalletClient.encode58Check(
            ByteArray.fromHexString(
                responseContent
                    .getJSONObject("raw_data")
                    .getJSONArray("contract")
                    .getJSONObject(0)
                    .getJSONObject("parameter")
                    .getJSONObject("value")
                    .getString("contract_address")));
    Assert.assertEquals(contractAddress, jsonObject.getString("contractAddress"));

    String ownerAddress =
        WalletClient.encode58Check(
            ByteArray.fromHexString(
                responseContent
                    .getJSONObject("raw_data")
                    .getJSONArray("contract")
                    .getJSONObject(0)
                    .getJSONObject("parameter")
                    .getJSONObject("value")
                    .getString("owner_address")));

    Assert.assertEquals(ownerAddress, jsonObject.getString("originAddress"));

    Assert.assertEquals(
        WalletClient.encode58Check(event001Address), jsonObject.getString("creatorAddress"));
  }

  private void expectInformationFromGetBlockByNum(JSONObject jsonObject, String txId) {

    response = HttpMethed.getTransactionInfoById(httpFullNode, txId);
    responseContent = HttpMethed.parseResponseContent(response);
    long blockNumber = responseContent.getLong("blockNumber");
    response = HttpMethed.getBlockByNum(httpFullNode, blockNumber);
    responseContent = HttpMethed.parseResponseContent(response);
    Assert.assertEquals(responseContent.getString("blockID"), jsonObject.getString("blockHash"));
  }

  /** constructor. */
  @AfterClass
  public void shutdown() throws InterruptedException {
    if (channelFull != null) {
      channelFull.shutdown().awaitTermination(5, TimeUnit.SECONDS);
    }
  }
}
