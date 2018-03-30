
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.MoreExecutors;
import org.bitcoinj.core.*;
import org.bitcoinj.crypto.KeyCrypterException;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.RegTestParams;
import org.bitcoinj.utils.BriefLogFormatter;
import org.bitcoinj.wallet.AllowUnconfirmedCoinSelector;
import org.bitcoinj.wallet.SendRequest;
import org.bitcoinj.wallet.Wallet;
import org.bitcoinj.wallet.listeners.WalletCoinsReceivedEventListener;

import java.io.File;

import static com.google.common.base.Preconditions.checkNotNull;


/**
 * ForwardingService sits on the bitcoin main network and when it receives coins in the selected wallet,
 * it sends them onwards to an address given.
 */
public class ForwardingService {

    //
//
    //    Modify the following parameters:
//    Private key of the address that needs to be monitored and have the funds forwarded from.
//    Private key also includes the public key/address so no need for that
    private static final String privateKey = "xxxx";
    //    Fee type is used to calculate the speed of confirming a transaction
//    FASTESTFEE: The lowest fee (in satoshis per byte) that will currently result in the fastest transaction confirmations (usually 0 to 1 block delay).
//    HALFHOURFEE: The lowest fee (in satoshis per byte) that will confirm transactions within half an hour (with 90% probability).
//    HOURFEE: The lowest fee (in satoshis per byte) that will confirm transactions within an hour (with 90% probability).
    private static final String feeType = FeeCalculator.FASTEST;
    //   Public key/address that will receive the forwarded funds
    private static final String addressForForwarding = "1FMw2Tr1tJj1JyD2udpthvY6VtbwrYReqT";
//
//


    private static Address forwardingAddress;
    private static WalletAppKit kit;


    public static void main(String[] args) throws Exception {
        // This line makes the log output more compact and easily read, especially when using the JDK log adapter.
        BriefLogFormatter.init();

        // Figure out which network we should connect to. Each one gets its own set of files.
        NetworkParameters params;
        String filePrefix;

        params = MainNetParams.get();
        filePrefix = "forwarding-service";

        // Parse the address given as the first parameter.
        forwardingAddress = LegacyAddress.fromBase58(params, addressForForwarding);

        System.out.println("Network: " + params.getId());
        System.out.println("Forwarding address: " + forwardingAddress);

        // Start up a basic app using a class that automates some boilerplate.
        kit = new WalletAppKit(params, new File("."), filePrefix) {
            @Override
            protected void onSetupCompleted() {
                // This is called in a background thread after startAndWait is called, as setting up various objects
                // can do disk and network IO that may cause UI jank/stuttering in wallet apps if it were to be done
                // on the main thread.

                ECKey key = DumpedPrivateKey.fromBase58(MainNetParams.get(), privateKey).getKey();

                System.out.println("Address from private key is: " + LegacyAddress.fromKey(params, key).toString());
                wallet().importKey(key);
            }
        };
        if (params == RegTestParams.get()) {
            // Regression test mode is designed for testing and development only, so there's no public network for it.
            // If you pick this mode, you're expected to be running a local "bitcoind -regtest" instance.
            kit.connectToLocalHost();
        }

        // Download the block chain and wait until it's done.
        kit.startAsync();
        kit.awaitRunning();

        // We want to know when we receive money.
        kit.wallet().addCoinsReceivedEventListener(new WalletCoinsReceivedEventListener() {
            @Override
            public void onCoinsReceived(Wallet w, Transaction tx, Coin prevBalance, Coin newBalance) {
                // Runs in the dedicated "user thread" (see bitcoinj docs for more info on this).
                //
                // The transaction "tx" can either be pending, or included into a block (we didn't see the broadcast).
                Coin value = tx.getValueSentToMe(w);
                System.out.println("Received tx for " + value.toFriendlyString() + ": " + tx);
                System.out.println("Transaction will be forwarded after it confirms.");
                // Wait until it's made it into the block chain (may run immediately if it's already there).
                //
                // For this dummy app of course, we could just forward the unconfirmed transaction. If it were
                // to be double spent, no harm done. Wallet.allowSpendingUnconfirmedTransactions() would have to
                // be called in onSetupCompleted() above. But we don't do that here to demonstrate the more common
                // case of waiting for a block.
                Futures.addCallback(tx.getConfidence().getDepthFuture(1), new FutureCallback<TransactionConfidence>() {
                    @Override
                    public void onSuccess(TransactionConfidence result) {
                        System.out.println("Confirmation received.");
                        forwardCoins(tx);
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        // This kind of future can't fail, just rethrow in case something weird happens.
                        throw new RuntimeException(t);
                    }
                });
            }
        });

        Address sendToAddress = LegacyAddress.fromKey(params, kit.wallet().currentReceiveKey());
        System.out.println("Send coins to: " + sendToAddress + "if you");
        System.out.println("Waiting for coins to arrive to " + sendToAddress);
        System.out.println("Press Ctrl-C to quit");

        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException ignored) {
        }
    }

    private static void forwardCoins(Transaction tx) {
        try {

            //Calculating selected fee type
            Coin fee = Coin.valueOf(FeeCalculator.extractOptimalFee(feeType));
            System.out.println("Forwarding coins");

            System.out.println("Retrieving fee +" + fee.toFriendlyString());
            System.out.println("Retrieving fee +" + fee.toPlainString());
            SendRequest sendRequest = SendRequest.emptyWallet(forwardingAddress);
            sendRequest.feePerKb = fee;
            sendRequest.emptyWallet = true;
            sendRequest.coinSelector = AllowUnconfirmedCoinSelector.get();

            Wallet.SendResult sendResult = kit.wallet().sendCoins(sendRequest);
            checkNotNull(sendResult);  // We should never try to send more coins than we have!
            System.out.println("Sending ...");
            // Register a callback that is invoked when the transaction has propagated across the network.
            // This shows a second style of registering ListenableFuture callbacks, it works when you don't
            // need access to the object the future returns.
            sendResult.broadcastComplete.addListener(new Runnable() {
                @Override
                public void run() {
                    // The wallet has changed now, it'll get auto saved shortly or when the app shuts down.
                    System.out.println("Sent coins onwards! Transaction hash is " + sendResult.tx.getHashAsString());
                }
            }, MoreExecutors.directExecutor());
        } catch (KeyCrypterException | InsufficientMoneyException e) {
            // We don't use encrypted wallets in this example - can never happen.
            throw new RuntimeException(e);
        }
    }

}
