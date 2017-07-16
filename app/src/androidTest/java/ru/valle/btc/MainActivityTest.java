/*
 * The MIT License (MIT)
 * <p/>
 * Copyright (c) 2013 Valentin Konovalov
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package ru.valle.btc;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.text.TextUtils;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

import external.ExternalPrivateKeyStorage;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNotSame;
import static junit.framework.Assert.assertTrue;

/**
 * You may want to comment out
 * testBuildType "release"
 * in build.gradle to run it straight from android studio.
 * or call "gradlew clean uninstallAll installRelease installReleaseAndroidTest connectedAndroidTest" to run all tests
 * from command line (this requires signing.properties file)
 */
@SuppressLint("SetTextI18n")
@RunWith(AndroidJUnit4.class)
public class MainActivityTest {
    @Rule
    public ActivityTestRule<MainActivity> activityRule = new ActivityTestRule<>(MainActivity.class);

    private EditText addressView;
    private EditText privateKeyTextEdit;
    private View qrAddressButton;

    @Before
    public void setUp() throws Exception {
        MainActivity mainActivity = activityRule.getActivity();
        addressView = mainActivity.findViewById(R.id.address_label);
        privateKeyTextEdit = mainActivity.findViewById(R.id.private_key_label);
        qrAddressButton = mainActivity.findViewById(R.id.qr_address_button);
    }

    @Test
    public void testAlwaysGenerateNewAddress() {
        Activity activity = activityRule.getActivity();
        String address = waitForAddress(activity);
        assertNotNull(address);
        activity.finish();
        activity = activityRule.launchActivity(null);
        assertFalse(activity.isFinishing());
        String anotherAddress = waitForAddress(activity);
        assertNotNull(anotherAddress);
        assertNotSame(address, anotherAddress);
    }

    @Test
    public void testLayoutOnStart() {
        Activity activity = activityRule.getActivity();
        assertTrue(activity.findViewById(R.id.send_layout).getVisibility() == View.GONE);
        assertTrue(activity.findViewById(R.id.spend_tx_description).getVisibility() == View.GONE);
        assertTrue(activity.findViewById(R.id.spend_tx).getVisibility() == View.GONE);
        activity.finish();
    }

    @Test
    public void testAddressGenerateOnStartup() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activityRule.getActivity());
        performGenerationTest(preferences, PreferencesActivity.PREF_PRIVATE_KEY_MINI);
        performGenerationTest(preferences, PreferencesActivity.PREF_PRIVATE_KEY_WIF_COMPRESSED);
        performGenerationTest(preferences, PreferencesActivity.PREF_PRIVATE_KEY_WIF_NOT_COMPRESSED);
    }

    private void performGenerationTest(SharedPreferences preferences, String privateKeyType) {
        preferences.edit().putString(PreferencesActivity.PREF_PRIVATE_KEY, privateKeyType).commit();
        activityRule.getActivity().finish();
        final MainActivity activity = activityRule.launchActivity(null);
        assertFalse(activityRule.getActivity().isFinishing());
        preferences = PreferenceManager.getDefaultSharedPreferences(activity);
        assertEquals(privateKeyType, preferences.getString(PreferencesActivity.PREF_PRIVATE_KEY, PreferencesActivity.PREF_PRIVATE_KEY_WIF_COMPRESSED));
        checkIfGeneratedKeyIsValid(privateKeyType);
        activity.runOnUiThread(new Runnable() {
            public void run() {
                assertTrue(activity.findViewById(R.id.spend_tx_description).getVisibility() == View.GONE);
                assertTrue(activity.findViewById(R.id.spend_tx).getVisibility() == View.GONE);
            }
        });
    }

    private void checkIfGeneratedKeyIsValid(String privateKeyType) {
        String address = waitForAddress(activityRule.getActivity());
        assertNotNull(address);
        assertTrue("Addresses must starts with '1', but generated address is '" + address + "'", address.startsWith("1"));
        String privateKey = getText(activityRule.getActivity(), R.id.private_key_label);
        assertNotNull(privateKey);
        if (PreferencesActivity.PREF_PRIVATE_KEY_MINI.equals(privateKeyType)) {
            assertTrue("Private keys must starts with 'S', but generated key is '" + privateKey + "'", privateKey.startsWith("S"));
            assertEquals("Private keys should have length 30 characters ", 30, privateKey.length());
        } else if (PreferencesActivity.PREF_PRIVATE_KEY_WIF_COMPRESSED.equals(privateKeyType)) {
            assertTrue("WIF private keys (compressed public) must starts with 'K' or 'L', but generated key is '" + privateKey + "'", privateKey.startsWith("K") || privateKey.startsWith("L"));
            byte[] decoded = BTCUtils.decodeBase58(privateKey);
            assertNotNull(decoded);
            assertEquals("decoded private key (with compressed public key) should be 38 bytes length", 38, decoded.length);
        } else if (PreferencesActivity.PREF_PRIVATE_KEY_WIF_NOT_COMPRESSED.equals(privateKeyType)) {
            assertTrue("WIF private keys (not compressed public) must starts with '5', but generated key is '" + privateKey + "'", privateKey.startsWith("5"));
            byte[] decoded = BTCUtils.decodeBase58(privateKey);
            assertNotNull(decoded);
            assertTrue("decoded private key (with not compressed public key) should be 37 or 38 bytes length", decoded.length == 37 || decoded.length == 38);
        }
    }

    @Test
    public void testDecodeMiniKey() {
        activityRule.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                privateKeyTextEdit.setText("S6c56bnXQiBjk9mqSYE7ykVQ7NzrRy");
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        String decodedAddress = waitForAddress(activityRule.getActivity());
        assertEquals("1CciesT23BNionJeXrbxmjc7ywfiyM4oLW", decodedAddress);
    }

    @Test
    public void testDecodeUncompressedWIF() {
        activityRule.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                privateKeyTextEdit.setText("5Kb8kLf9zgWQnogidDA76MzPL6TsZZY36hWXMssSzNydYXYB9KF");
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        String decodedAddress = waitForAddress(activityRule.getActivity());
        assertEquals("1CC3X2gu58d6wXUWMffpuzN9JAfTUWu4Kj", decodedAddress);
    }

    @Test
    public void testDecodeCompressedWIF() {
        activityRule.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                privateKeyTextEdit.setText("KwntMbt59tTsj8xqpqYqRRWufyjGunvhSyeMo3NTYpFYzZbXJ5Hp");
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        String decodedAddress = waitForAddress(activityRule.getActivity());
        assertEquals("1Q1pE5vPGEEMqRcVRMbtBK842Y6Pzo6nK9", decodedAddress);
    }

    @Test
    public void testDecodeAddress() {
        activityRule.getActivity().runOnUiThread(
                new Runnable() {
                    public void run() {
                        checkDecodeAddress();
                    }
                }
        );
    }

    private void checkDecodeAddress() {
        activityRule.getActivity().runOnUiThread(new Runnable() {
            @SuppressLint("SetTextI18n")
            public void run() {
                addressView.setText("weriufhwehfiow");
                assertEquals("Address qr code button should be visible when an invalid address entered", View.GONE, qrAddressButton.getVisibility());
                addressView.setText("1CciesT23BNionJeXrbxmjc7ywfiyM4oLW");
                assertEquals("You may edit address field", "1CciesT23BNionJeXrbxmjc7ywfiyM4oLW", getString(addressView));
                assertEquals("Typing in address field should clean private key", "", getString(privateKeyTextEdit));
                assertEquals("Address qr code button should be visible when a valid address entered", View.VISIBLE, qrAddressButton.getVisibility());
            }
        });
    }

    @Test
    public void testDecodeAddressAndWait() {
        checkDecodeAddress();
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        activityRule.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                assertEquals("You may edit address field and the change must persist", "1CciesT23BNionJeXrbxmjc7ywfiyM4oLW", getString(addressView));
                assertEquals("Typing in address field should clean private key and the change must persist", "", getString(privateKeyTextEdit));
            }
        });
    }

    @Test
    public void testTxCreationFromUI() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activityRule.getActivity());
        long extraFee = 7;
        preferences.edit().putLong(PreferencesActivity.PREF_EXTRA_FEE, extraFee).commit();
        checkTxCreationFromUI("L49guLBaJw8VSLnKGnMKVH5GjxTrkK4PBGc425yYwLqnU5cGpyxJ", null, "1NKkKeTDWWi5LQQdrSS7hghnbhfYtWiWHs",
                "0100000001ef9ea3e6b7a664ff910ed1177bfa81efa018df417fb1ee964b8165a05dc7ef5a000000008b4830450220385373efe509" +
                        "719e38cb63b86ca5d764be0f2bd2ffcfa03194978ca68488f57b0221009686e0b54d7831f9f06d36bfb81c5d2931a8ada079a3ff58c" +
                        "6109030ed0c4cd601410424161de67ec43e5bfd55f52d98d2a99a2131904b25aa08e70924d32ed44bfb4a71c94a7c4fdac886ca5bec7" +
                        "b7fac4209ab1443bc48ab6dec31656cd3e55b5dfcffffffff02707f0088000000001976a9143412c159747b9149e8f0726123e2939b68" +
                        "edb49e88ace0a6e001000000001976a914e9e64aae2d1e066db6c5ecb1a2781f418b18eef488ac00000000",
                "1AyyaMAyo5sbC73kdUjgBK9h3jDMoXzkcP", BTCUtils.MIN_FEE_PER_KB + extraFee, 31500000 - BTCUtils.MIN_FEE_PER_KB - extraFee);


        checkTxCreationFromUI("L49guLBaJw8VSLnKGnMKVH5GjxTrkK4PBGc425yYwLqnU5cGpyxJ", null, "1NKkKeTDWWi5LQQdrSS7hghnbhfYtWiWHs",
                "{\n" +
                        "\t \n" +
                        "\t\"unspent_outputs\":[\n" +
                        "\t\n" +
                        "\t\t{\n" +
                        "\t\t\t\"tx_hash\":\"088676b3e6cfb2f25e35f903b812ddae897ac922653c6ad6b74a188a08ffd253\",\n" +
                        "\t\t\t\"tx_output_n\": 1,\t\n" +
                        "\t\t\t\"script\":\"76a914e9e64aae2d1e066db6c5ecb1a2781f418b18eef488ac\",\n" +
                        "\t\t\t\"value\": 31500000,\n" +
                        "\t\t\t\"confirmations\":0\n" +
                        "\t\t}\n" +
                        "\t  \n" +
                        "\t]\n" +
                        "}",
                "18D5fLcryBDf8Vgov6JTd9Taj81gNekrex", BTCUtils.MIN_FEE_PER_KB + extraFee, 31500000 - BTCUtils.MIN_FEE_PER_KB - extraFee);

        checkTxCreationFromUI(ExternalPrivateKeyStorage.PRIVATE_KEY_FOR_1AuEGCuHeioQsvSuBYiX2cuNhoZVW7KfWK, null, "1AuEGCuHeioQsvSuBYiX2cuNhoZVW7KfWK",
                "\"unspent_outputs\":[\n" +
                        "\t\n" +
                        "\t\t{\n" +
                        "\t\t\t\"tx_hash\":\"ec875732e94898a294c7f83080b729a4d2d12f54aa357cb3edbb38c7ac26973a\",\n" +
                        "\t\t\t\"tx_hash_big_endian\":\"3a9726acc738bbedb37c35aa542fd1d2a429b78030f8c794a29848e9325787ec\",\n" +
                        "\t\t\t\"tx_index\":30464843,\n" +
                        "\t\t\t\"tx_output_n\": 1,\n" +
                        "\t\t\t\"script\":\"76a9146c99d52fba48aaf56de0cc26497a01f00328dd8a88ac\",\n" +
                        "\t\t\t\"value\": 380000,\n" +
                        "\t\t\t\"value_hex\": \"05cc60\",\n" +
                        "\t\t\t\"confirmations\":110025\n" +
                        "\t\t}\n" +
                        "\t  \n" +
                        "\t]",
                "18D5fLcryBDf8Vgov6JTd9Taj81gNekrex", BTCUtils.MIN_FEE_PER_KB + extraFee, 380000 - BTCUtils.MIN_FEE_PER_KB - extraFee);

    }

    @Test
    public void testTxCreationFromUIUsingBIP38Key() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(activityRule.getActivity());
        long extraFee = 0;
        preferences.edit().putLong(PreferencesActivity.PREF_EXTRA_FEE, extraFee).commit();

        checkTxCreationFromUI(ExternalPrivateKeyStorage.ENCRYPTED_PRIVATE_KEY_FOR_1AuEGCuHeioQsvSuBYiX2cuNhoZVW7KfWK, ExternalPrivateKeyStorage.PASSWORD_FOR_1AuEGCuHeioQsvSuBYiX2cuNhoZVW7KfWK, "1AuEGCuHeioQsvSuBYiX2cuNhoZVW7KfWK",
                "\n" +
                        "\"unspent_outputs\":[\n" +
                        "\t\n" +
                        "\t\t{\n" +
                        "\t\t\t\"tx_hash\":\"ec875732e94898a294c7f83080b729a4d2d12f54aa357cb3edbb38c7ac26973a\",\n" +
                        "\t\t\t\"tx_hash_big_endian\":\"3a9726acc738bbedb37c35aa542fd1d2a429b78030f8c794a29848e9325787ec\",\n" +
                        "\t\t\t\"tx_index\":30464843,\n" +
                        "\t\t\t\"tx_output_n\": 1,\n" +
                        "\t\t\t\"script\":\"76a9146c99d52fba48aaf56de0cc26497a01f00328dd8a88ac\",\n" +
                        "\t\t\t\"value\": 380000,\n" +
                        "\t\t\t\"value_hex\": \"05cc60\",\n" +
                        "\t\t\t\"confirmations\":110025\n" +
                        "\t\t}\n" +
                        "\t  \n" +
                        "\t]",
                "18D5fLcryBDf8Vgov6JTd9Taj81gNekrex", BTCUtils.MIN_FEE_PER_KB, 380000 - BTCUtils.MIN_FEE_PER_KB);
    }

    private void checkTxCreationFromUI(final String privateKey, final String password, final String expectedAddressForTheKey, final String unspentTxInfo, final String recipientAddress, long expectedFee, long expectedAmountInFirstOutput) {
        activityRule.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                ((EditText) activityRule.getActivity().findViewById(R.id.address_label)).setText("");
                ((EditText) activityRule.getActivity().findViewById(R.id.private_key_label)).setText(privateKey);
                if (!TextUtils.isEmpty(password)) {
                    ((EditText) activityRule.getActivity().findViewById(R.id.password_edit)).setText(password);

                }
            }
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        String decodedAddress = null;
        if (!TextUtils.isEmpty(password)) {
            boolean readyForDecryption = false;
            for (int i = 0; i < 100; i++) {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                String generatedAddress = getText(activityRule.getActivity(), R.id.address_label);
                if (!TextUtils.isEmpty(generatedAddress)) {
                    if (generatedAddress.startsWith("1")) {
                        decodedAddress = generatedAddress;
                        break;
                    } else if (activityRule.getActivity().getString(R.string.not_decrypted_yet).equals(generatedAddress)) {
                        readyForDecryption = true;
                        break;
                    }
                }
            }
            if (readyForDecryption) {
                activityRule.getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        Button button = ((Activity) activityRule.getActivity()).findViewById(R.id.password_button);
                        button.performClick();
                    }
                });
                InstrumentationRegistry.getInstrumentation().waitForIdleSync();
                decodedAddress = waitForAddress(activityRule.getActivity());
            }
        } else {
            decodedAddress = waitForAddress(activityRule.getActivity());
        }

        assertEquals(expectedAddressForTheKey, decodedAddress);
        activityRule.getActivity().runOnUiThread(new Runnable() {
            public void run() {
                ((EditText) activityRule.getActivity().findViewById(R.id.amount)).setText("");
                ((EditText) activityRule.getActivity().findViewById(R.id.raw_tx)).setText(unspentTxInfo);
                ((EditText) activityRule.getActivity().findViewById(R.id.recipient_address)).setText(recipientAddress);
            }
        });
        String createdTx = null;
        for (int i = 0; i < 100; i++) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            createdTx = getText(activityRule.getActivity(), R.id.spend_tx);
            if (!TextUtils.isEmpty(createdTx)) {
                break;
            }
        }
        assertNotNull(createdTx);

        ArrayList<UnspentOutputInfo> unspentOutputs = new ArrayList<>();
        byte[] rawTx = BTCUtils.fromHex(unspentTxInfo);
        if (rawTx != null) {
            Transaction baseTx = null;
            try {
                baseTx = new Transaction(rawTx);
            } catch (BitcoinException ignored) {
            }
            assertNotNull(baseTx);
            byte[] rawTxReconstructed = baseTx.getBytes();
            if (!Arrays.equals(rawTxReconstructed, rawTx)) {
                throw new IllegalArgumentException("Unable to decode given transaction");
            }
            byte[] txHash = BTCUtils.reverse(BTCUtils.doubleSha256(rawTx));
            for (int outputIndex = 0; outputIndex < baseTx.outputs.length; outputIndex++) {
                Transaction.Output output = baseTx.outputs[outputIndex];
                unspentOutputs.add(new UnspentOutputInfo(txHash, output.script, output.value, outputIndex, -1));
            }
        } else {
            try {
                String jsonStr = unspentTxInfo.replace((char) 160, ' ').trim();//remove nbsp
                if (!jsonStr.startsWith("{")) {
                    jsonStr = "{" + jsonStr;
                }
                if (!jsonStr.endsWith("}")) {
                    jsonStr += "}";
                }
                JSONObject jsonObject = new JSONObject(jsonStr);
                JSONArray unspentOutputsArray = jsonObject.getJSONArray("unspent_outputs");
                for (int i = 0; i < unspentOutputsArray.length(); i++) {
                    JSONObject unspentOutput = unspentOutputsArray.getJSONObject(i);
                    byte[] txHash = BTCUtils.reverse(BTCUtils.fromHex(unspentOutput.getString("tx_hash")));
                    Transaction.Script script = new Transaction.Script(BTCUtils.fromHex(unspentOutput.getString("script")));
                    long value = unspentOutput.getLong("value");
                    int outputIndex = unspentOutput.getInt("tx_output_n");
                    long confirmations = unspentOutput.has("confirmations") ? unspentOutput.getLong("confirmations") : -1;
                    unspentOutputs.add(new UnspentOutputInfo(txHash, script, value, outputIndex, confirmations));
                }
            } catch (Exception e) {
                assertFalse(e.getMessage(), true);
            }
        }

        Transaction spendTx = null;
        try {
            spendTx = new Transaction(BTCUtils.fromHex(createdTx));
        } catch (BitcoinException ignored) {
        }
        assertNotNull(spendTx);
        long inValue = 0;
        for (Transaction.Input input : spendTx.inputs) {
            for (UnspentOutputInfo unspentOutput : unspentOutputs) {
                if (Arrays.equals(unspentOutput.txHash, input.outPoint.hash) && unspentOutput.outputIndex == input.outPoint.index) {
                    inValue += unspentOutput.value;
                }
            }
        }
        long outValue = 0;
        for (Transaction.Output output : spendTx.outputs) {
            outValue += output.value;
        }
        long fee = inValue - outValue;
        assertEquals(expectedFee, fee);
        assertEquals(expectedAmountInFirstOutput, spendTx.outputs[0].value);

        try {
            Transaction.Script[] relatedScripts = new Transaction.Script[spendTx.inputs.length];
            for (int i = 0; i < spendTx.inputs.length; i++) {
                Transaction.Input input = spendTx.inputs[i];
                for (UnspentOutputInfo unspentOutput : unspentOutputs) {
                    if (Arrays.equals(unspentOutput.txHash, input.outPoint.hash) && unspentOutput.outputIndex == input.outPoint.index) {
                        relatedScripts[i] = unspentOutput.script;
                        break;
                    }
                }
                assertNotNull("and where is unspent output's script for this input?", relatedScripts[i]);
            }
            BTCUtils.verify(relatedScripts, spendTx);
        } catch (Transaction.Script.ScriptInvalidException e) {
            assertFalse(e.getMessage(), true);
        }
    }

    private String waitForAddress(Activity activity) {
        for (int i = 0; i < 150; i++) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            String generatedAddress = getText(activity, R.id.address_label);
            if (!TextUtils.isEmpty(generatedAddress) && generatedAddress.startsWith("1")) {
                return generatedAddress;
            }
        }
        return null;
    }

    private String getText(final Activity activity, final int id) {
        FutureTask<String> task = new FutureTask<>(new Callable<String>() {
            @Override
            public String call() throws Exception {
                TextView textView = activity.findViewById(id);
                return textView.getVisibility() == View.VISIBLE ? getString(textView) : null;
            }
        });
        activity.runOnUiThread(task);
        try {
            return task.get();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (ExecutionException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String getString(TextView textView) {
        CharSequence charSequence = textView == null ? null : textView.getText();
        return charSequence == null ? "" : charSequence.toString();
    }


}
