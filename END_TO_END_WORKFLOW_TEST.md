# END-TO-END WORKFLOW TESTING GUIDE
**Date**: March 26, 2026  
**Objective**: Validate complete service request lifecycle from creation to auto-completion

---

## 🎯 Test Scenario Overview

We will create a **Service Request for ASO document**, automatically assign it to a technician, have the technician create and sign the ASO document, and verify that the request auto-completes.

### Expected Flow
```
1. Clinic creates service request (ASO document needed)
   ↓
2. System auto-assigns request to available technician
   ↓
3. Technician receives notification
   ↓
4. Technician creates and fills ASO document
   ↓
5. Technician signs ASO document
   ↓
6. Service request auto-transitions to COMPLETED
   ↓
7. Clinic sees request as complete
```

---

## 📋 Pre-Test Checklist

### Required Setup
- [x] App installed on test device (already done)
- [x] APK built (already done)
- [x] Database initialized
- [ ] Test company created
- [ ] Test technicians created
- [ ] Test employees assigned to technicians

### Device Selection
**Primary Test Device**: Xiaomi (RX2XB0226LL)  
**Secondary Test**: Samsung tablet (a6776d23)  
**Tertiary Test**: Emulator (emulator-5554)

---

## 🛠️ Step-by-Step Test Procedure

### PHASE 1: Setup Test Data

#### 1.1 Create Test Company (via CompanyManagementActivity)
1. Open Anda app on Xiaomi device
2. Complete LGPD consent screen
3. Select "Clinic" profile (for testing)
4. Navigate to **Companies** section
5. Click **"Add Company"**
6. Enter test company data:
   - **CNPJ**: 20.074.884/0001-36 (use Nancy Rezende's actual CNPJ)
   - Click **"Lookup CNPJ"** button
   - Verify it auto-fills with:
     - Legal Name: NANCY REZENDE DE LIMA
     - City: ARAUCARIA
     - State: PR
   - Click **Save**

✅ **Expected Outcome**: Company appears in company list

---

#### 1.2 Create Test Technicians (via EmployeeManagementActivity)
1. From app menu, navigate to **Employees** section
2. Click **"Add Employee"**
3. Create Technician #1:
   - **Name**: João Silva
   - **CPF**: 111.111.111-11 (fake/test)
   - **Role**: Médico (Medical doctor)
   - **Company**: Select "NANCY REZENDE DE LIMA"
   - **Notes**: "ASO, PCMSO, 10 anos" (to indicate: certified for ASO/PCMSO, 10 years experience)
   - Click **Save**

4. Create Technician #2 (optional):
   - **Name**: Maria Santos
   - **CPF**: 222.222.222-22
   - **Role**: Engenheiro de Segurança (Safety engineer)
   - **Company**: Select "NANCY REZENDE DE LIMA"
   - **Notes**: "NR10, NR12, 5 anos"
   - Click **Save**

✅ **Expected Outcome**: Both employees visible in employee list

---

### PHASE 2: Create Service Request

#### 2.1 Create Request for ASO Document
1. Navigate to **Service Requests** section
2. Fill in the form:
   - **Contractor Name**: NANCY REZENDE DE LIMA (or select from dropdown if available)
   - **Contractor CNPJ**: 20.074.884/0001-36
   - **Document Type**: ASO
   - **WhatsApp**: 41999999999 (test number)
   - **Preferred Contact**: Check "Prefer WhatsApp"
   - **Notes**: "Annual occupational health exam"
3. Click **"Save Request"**

✅ **Expected Outcome**: 
- Toast: "Request saved and assigned to João Silva" (or similar)
- Request appears in list with status "ASSIGNED"
- Request code is displayed (format: e.g., "REQ-NANCY-001")

---

#### 2.2 Verify Auto-Assignment
1. Check request card details:
   - **Status**: Should show "ASSIGNED" (not "OPEN")
   - **Assigned To**: Should show "João Silva"
   - **Updated**: Should show current date/time

✅ **Expected Outcome**: Auto-assignment engine selected João Silva because:
- He's certified for ASO (inferred from "Médico" role + "ASO" in notes)
- He has no active requests (least busy)
- He has 10 years experience (good bonus)

---

#### 2.3 Check Assignment Notification
If testing on physical device with notifications:
1. Look for notification: "New service request assigned"
2. Tap notification
3. Verify it opens ServiceRequestsActivity with request focused

✅ **Expected Outcome**: Deep-link works, shows request details

---

### PHASE 3: Create and Sign Document

#### 3.1 Switch to Technician View
1. Open app menu
2. Go to **Settings** or profile selector
3. Switch user profile to **Technician**
4. Select "João Silva" from technician list
5. Confirm PIN/biometric unlock

✅ **Expected Outcome**: UI changes to technician mode
- Form for creating requests disappears
- Only assigned requests are shown
- Action buttons: "Start Work" → "Mark Done"

---

#### 3.2 Verify Request is Visible to Technician
1. Technician should see the request:
   - **Request Code**: REQ-NANCY-001 (or whatever was generated)
   - **Status**: ASSIGNED
   - **Document Type**: ASO
   - **Company**: NANCY REZENDE DE LIMA

✅ **Expected Outcome**: Request appears in technician's assigned requests list

---

#### 3.3 Start Work on Request
1. Click **"Start Work"** button on the request
2. Request should transition to **IN_PROGRESS**
3. System should suggest creating ASO document

✅ **Expected Outcome**: 
- Request status updates to IN_PROGRESS
- Button changes to "Mark Done"

---

#### 3.4 Create and Sign ASO Document
1. From request detail view, click **"Create ASO Document"** (or navigate via Documents menu)
2. Fill ASO form with test data:
   - **Company**: NANCY REZENDE DE LIMA (auto-filled from company)
   - **Employee**: João Silva (auto-filled from technician)
   - **Exam Type**: Admissional (Admission exam)
   - **Health Status**: Apto (Fit for work)
   - [Other required fields as per form]
3. Click **"Save Draft"**

✅ **Expected Outcome**:
- Document saves with status "DRAFT"
- Request status may transition to IN_PROGRESS (triggered by DRAFT_SAVED event)

---

#### 3.5 Sign the ASO Document
1. Document detail view should have **"Sign Document"** button
2. Click **"Sign Document"**
3. System may ask for PIN/biometric confirmation
4. Document transitions to "SIGNED" status

✅ **Expected Outcome**:
- Document now shows status "SIGNED"
- **Timestamp** of signature is recorded
- **Audit trail** shows signature event

---

### PHASE 4: Verify Auto-Completion

#### 4.1 Check Request Status
1. Return to Service Requests view
2. Refresh the request list
3. Find the request we created earlier (REQ-NANCY-001)

✅ **Expected Outcome**:
- **Status**: Should now be **COMPLETED** (not IN_PROGRESS)
- **Updated Time**: Should reflect the signature time
- **Notes**: May include audit entry like:
  ```
  [ANDA-LINK][26/03/2026 14:32] req=REQ-NANCY-001 type=ASO doc=ASO-123 SIGNED status=COMPLETED
  ```

---

#### 4.2 Switch Back to Clinic View
1. Change profile back to **Clinic Manager**
2. Go to **Service Requests**
3. Look for the request (REQ-NANCY-001)

✅ **Expected Outcome**:
- Clinic sees same request as "COMPLETED"
- Clinic can see which technician completed it
- Clinic can access the signed document

---

#### 4.3 Export PDF (Optional Advanced Test)
1. If PDF export is available:
2. Open the signed ASO document
3. Click **"Export to PDF"** button
4. Verify PDF is generated and contains:
   - All document data
   - Digital signature indicator
   - Date/time stamps

✅ **Expected Outcome**:
- PDF file created successfully
- Request status remains COMPLETED (PDF export also triggers auto-completion if it wasn't already)

---

## 📊 Test Results Summary Template

### Test Execution Log

**Date**: _________  
**Device**: _________ (Xiaomi / Samsung / Emulator)  
**Tester**: _________  

#### Phase 1: Setup
- [ ] Company created successfully
- [ ] Technician "João Silva" created
- [ ] Technician "Maria Santos" created (optional)

#### Phase 2: Request Creation
- [ ] Service request created (REQ code: _________)
- [ ] Auto-assignment executed
- [ ] Assigned to: _________________
- [ ] Request shows ASSIGNED status

#### Phase 3: Document Work
- [ ] Technician can see request
- [ ] Request transitions to IN_PROGRESS
- [ ] ASO document created and saved
- [ ] ASO document signed successfully

#### Phase 4: Auto-Completion
- [ ] Request status changed to COMPLETED ✅/❌
- [ ] Audit trail entry created ✅/❌
- [ ] Clinic sees request as completed ✅/❌

#### Optional: PDF Export
- [ ] PDF generated successfully ✅/❌
- [ ] PDF contains all data ✅/❌
- [ ] Digital signature visible ✅/❌

---

## 🚨 Troubleshooting

### Issue: Request not auto-assigned
**Symptom**: Status remains "OPEN" after creation  
**Root Cause**: No matching technicians found  
**Solution**: 
- Verify technicians were created
- Check role/notes for certification keywords
- Try creating technician with "ALL" certification

---

### Issue: Status doesn't update to COMPLETED
**Symptom**: Request stuck in "IN_PROGRESS" after signing  
**Root Cause**: Document event hook not firing  
**Solution**:
- Check that document was actually signed (not just drafted)
- Verify `DocumentLocalRepository.linkRequestForDocument()` is called
- Check logcat for errors: `adb logcat | grep -i "request\|lifecycle"`

---

### Issue: Deep-link doesn't work from notification
**Symptom**: Tapping notification opens generic app, not specific request  
**Root Cause**: Intent extras not passed correctly  
**Solution**:
- Check notification builder includes extras
- Verify `ServiceRequestsActivity` handles `EXTRA_FOCUS_REQUEST_CODE`

---

### Issue: Technician doesn't see request
**Symptom**: Request missing from technician's list  
**Root Cause**: Technician CPF doesn't match assignment  
**Solution**:
- Verify technician CPF is correctly stored in assignment
- Check that technician switched to correct profile
- Manually assign request to technician if auto-assignment fails

---

## 🎓 Key Concepts Validated

This test validates:

1. **Service Request Lifecycle**
   - ✅ OPEN → ASSIGNED (auto) → IN_PROGRESS → COMPLETED (auto)
   - ✅ Status transitions are automatic, not manual

2. **Request-Document Linking**
   - ✅ When document is signed, request auto-completes
   - ✅ Audit trail records the linkage
   - ✅ No schema migration needed (stored in notes field)

3. **Auto-Assignment Engine**
   - ✅ Selects technician based on certification + availability
   - ✅ Considers workload and experience
   - ✅ Falls back to generalists if no specialist available

4. **Multi-Role Workflow**
   - ✅ Clinic creates request in clinic view
   - ✅ Technician sees request in technician view
   - ✅ Both see same request with different permissions

5. **Notifications & Deep-Linking**
   - ✅ Technician notified of new assignment
   - ✅ Tapping notification opens correct request
   - ✅ Request is focused/highlighted in list

---

## 📝 Next Steps After Testing

### If Tests Pass (Green ✅)
1. Document test results in SESSION_SUMMARY
2. Mark "End-to-End Workflow" as COMPLETE
3. Move to next priority: **PDF Export & Digital Signature**

### If Tests Fail (Red ❌)
1. Document failure in troubleshooting section
2. Identify root cause (check logs)
3. Fix implementation
4. Re-run failed test

### Performance Metrics to Track
- Time from request creation to auto-assignment: _______ ms
- Time from document signature to request completion: _______ ms
- Notification delivery time: _______ ms
- Deep-link response time: _______ ms

---

## 📞 Support

**To view app logs**: 
```bash
adb -s RX2XB0226LL logcat | grep -i "service\|request\|assignment\|lifecycle"
```

**To clear test data and restart**:
```bash
adb shell pm clear com.example.anda
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**To record video of test**:
```bash
adb -s RX2XB0226LL shell screenrecord /sdcard/test.mp4
# (ctrl+c after 2 minutes)
adb pull /sdcard/test.mp4
```

---

**Created**: March 26, 2026  
**Status**: Ready for Testing  
**Estimated Duration**: 30-45 minutes  

📌 **KEY**: This test validates the CORE VALUE PROPOSITION of the app. All other features build on this foundation.

