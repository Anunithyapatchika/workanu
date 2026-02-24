# 🔴 API KEY FIX - Step-by-Step Guide

## Current Error

```
401 PermissionDenied on POST request for OpenAI API
```

**Translation:** Your API key is missing or wrong.

---

## ✅ What's Working (Everything Else!)

Your logs show:
```
✅ Config - TopN: 25, Temp: 0.15, Strictness: 1, InScope: false ✅
✅ Session tracking working ✅
✅ Printrail tracking working ✅
✅ Context enhancement working ✅
```

**Only problem:** API authentication

---

## 🎯 Solution: Fix API Key

### Step 1: Get Your OpenAI API Key

**Method A: From Azure Portal**

1. Go to: **https://portal.azure.com**
2. In search, type: **`openai-ecedel-aipoc`**
3. Click on it (Azure OpenAI Service)
4. Left menu → **"Keys and Endpoint"**
5. **Copy KEY 1** (the long string)
6. Save it temporarily in a text file

**Method B: Ask Your Team**

If you don't have access:
- Ask your Azure admin for the OpenAI API key
- They can find it in Azure OpenAI resource → Keys and Endpoint

---

### Step 2: Add Key to Azure Key Vault

**Option A: Via Azure Portal (Recommended)**

1. Go to Azure Portal
2. Search for your Key Vault
   - Probably named: `kv-ecedel-stage2` or similar
   - Or search in resource group: `rg-ecedel-stage2`
3. Click on it
4. Left menu → **"Secrets"**
5. Click: **"+ Generate/Import"**
6. Fill in:
   - **Name:** `openai-api-key` (EXACTLY this!)
   - **Value:** [Paste the API key from Step 1]
   - **Content type:** (leave empty)
   - **Enabled:** Yes
7. Click: **"Create"**

**Verify it was created:**
- You should see `openai-api-key` in the secrets list
- Click on it
- Click on current version
- Click "Show Secret Value" to verify it's correct

---

### Step 3: Verify App Service Can Access Key Vault

**Check Access Policy:**

1. Still in Key Vault
2. Left menu → **"Access policies"**
3. Look for: `usp-financial-reporting-service-ecedel-stage2`
4. Should have: **"Secret - Get"** permission

**If NOT found:**
1. Click: "+ Add Access Policy"
2. Secret permissions → Check **"Get"**
3. Select principal → Search: `usp-financial-reporting-service-ecedel-stage2`
4. Click: Add
5. Click: Save (very important!)

---

### Step 4: Update OpenAIConfig.java (Optional but Recommended)

Replace your current `OpenAIConfig.java` with the improved version that:
- Tries multiple API key names
- Logs whether key is found
- Doesn't expose the actual key in logs

Use the **OpenAIConfig-IMPROVED.java** file provided.

---

### Step 5: Restart App

```bash
az webapp restart \
  --resource-group <your-resource-group> \
  --name usp-financial-reporting-service-ecedel-stage2
```

Wait 3-4 minutes for restart.

---

### Step 6: Verify in Logs

1. Go to Azure Portal → App Service → Log stream
2. Look for:

**Good (Key Found):**
```
✅ OpenAI API Key loaded successfully (sk-p****...)
```

**Bad (Key Missing):**
```
🔴 CRITICAL: OpenAI API Key is NOT configured!
```

---

## 🔧 Alternative: Temporary Quick Fix

If you can't access Key Vault right now, temporarily add to `application-stage2.yml`:

Add at the very end of the file:

```yaml
# Temporary API key (REMOVE BEFORE GIT COMMIT!)
openai-api-key: "sk-proj-YOUR-ACTUAL-KEY-HERE"
```

Then:
1. Build and deploy
2. Test
3. **REMOVE THIS LINE** before committing to Git!
4. Set up Key Vault properly later

⚠️ **WARNING:** Never commit API keys to Git! This is only for local testing.

---

## 🎯 After Fixing API Key

Once the key is configured, your chatbot will:

1. ✅ Connect to OpenAI successfully
2. ✅ Get complete answers (TopN: 25, MaxTokens: 4096)
3. ✅ Remember context (session tracking)
4. ✅ Understand vague questions (context enhancement)
5. ✅ Track printrails correctly

**Everything else is already working!** Just need the API key.

---

## 📋 Quick Checklist

- [ ] Get OpenAI API key from Azure Portal
- [ ] Add to Azure Key Vault as `openai-api-key`
- [ ] Verify App Service has Key Vault access
- [ ] (Optional) Update OpenAIConfig.java with improved version
- [ ] Restart app
- [ ] Check logs for "✅ OpenAI API Key loaded"
- [ ] Test chatbot
- [ ] Done! 🎉

---

## ❓ Troubleshooting

### Still Getting 401 Error?

**Check 1: Is the key correct?**
- Go to Azure OpenAI resource
- Compare KEY 1 with what's in Key Vault
- They should match exactly

**Check 2: Is Key Vault configured in app?**
- Check `bootstrap.yml` or `application-stage2.yml`
- Should have Key Vault connection configured

**Check 3: Is Managed Identity enabled?**
- App Service → Settings → Identity
- System assigned: Should be "On"

---

## 🎉 Expected Result

After fixing:

```
User: "Give us LOB for PT3450"
Bot: "PT3450 serves the [actual LOB from documents]..."

✅ Works perfectly!
```

---

**Bottom line:** Your code is perfect! Just add the API key to Azure Key Vault and it will work! 🚀
