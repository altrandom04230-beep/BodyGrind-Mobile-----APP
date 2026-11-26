# Trainer Portal & Trainer Profile Management

A comprehensive, role-enforced Trainer Portal and Profile Management module for BodyGrind. Enables Trainers to view, edit, and maintain their professional profiles and upload certificates to Firebase Storage, while granting Admins centralized oversight to manage trainer records, approve or reject certificates, and assign trainer profiles to existing users.

---

### User Review & Critical Decisions

> [!IMPORTANT]
> The architectural directions confirmed during the interview phase are incorporated:
> - **Collection Choice**: Dedicated top-level `trainers` collection in Cloud Firestore.
> - **Role Schema**: Support the new `role` field (`admin`, `trainer`, `member`) while maintaining full backwards compatibility with existing uppercase `accountType` (`ADMIN`, `TRAINER`, `MEMBER`).
> - **Navigation Placement**: Anchored as a dedicated **Account / Portal** section tab within the `TrainerMainScreen` and accessible to Admins.

---

## 1. Overview & Core Concept

- **What It Does**: 
  - Provides a **Trainer Account** section within the app interface.
  - Trainers can view their current details, edit their Name, Email, Contact (digits only), Specialization, and upload/replace certification documents (PDF, JPG, PNG).
  - Certificate uploads automatically set certification status to `pending`.
  - Admins can list all trainer profiles, filter by certificate status, add a trainer record for an existing user ID, edit/delete any trainer profile, and review certificates with one-tap `Approve` or `Reject` actions.
  - Members are strictly barred from accessing the Trainer Portal.
- **Target Audience**:
  - **Trainers**: Managing professional credentials and client-facing profile data.
  - **Admins**: Gym operators verifying fitness credentials and managing trainer rosters.

---

## 2. User Experience & Visual Design

```
┌───────────────────────────────────────────────────────────────┐
│ Trainer Portal / Account                                      │
├───────────────────────────────────────────────────────────────┤
│ [ Trainer Profile Card ]                                      │
│ • Name: Rico Santos                                           │
│ • Email: rico.santos@bodygrind.com                            │
│ • Phone: 09171234567                                          │
│ • Specialization: Strength, Conditioning & Hypertrophy        │
│                                                               │
│ [ Certification Status ]                                      │
│ Status: [ PENDING / APPROVED / REJECTED ]                     │
│ File: ACE_Certified_Trainer.pdf (2.4 MB)                      │
│ [ Upload / Replace Certificate ]                              │
│                                                               │
│ (If Admin View)                                               │
│ [ All Trainers List ] [ + Add Trainer ]                       │
│ • Coach Rico Santos — [ Approved ] [ Edit ] [ Delete ]        │
│ • Coach Elena Gomez — [ Pending ]  [ Review: Approve/Reject ] │
└───────────────────────────────────────────────────────────────┘
```

- **Key User Flows**:
  - **Trainer Flow**: Navigate to Account tab -> View personal profile -> Tap "Edit Profile" -> Update name, contact, specialization -> Pick certificate (Android Photo Picker or Document Picker) -> Upload shows progress indicator -> Save updates profile and marks certificate as `pending`.
  - **Admin Flow**: Access Trainer Management in Portal -> View cards for all trainers -> Tap "Add Trainer" to link an existing user -> Tap certificate thumbnail to preview -> Tap "Approve" (turns green) or "Reject" (turns red with optional feedback) -> Changes sync immediately in Firestore.
- **Visual Identity & Theme**:
  - Strictly follows BodyGrind's black-and-red design system (`PureBlack`, `DarkCharcoal`, `BoldRed`, `FlameOrange`).
  - Status Pills:
    - `Approved`: `SuccessGreen` background / text.
    - `Pending`: `WarningAmber` background / text.
    - `Rejected`: `DangerRed` background / text.
- **Input Validation Rules**:
  - **Name**: Must be non-empty (1-80 characters, trimmed).
  - **Email**: Standard email pattern (`^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}$`).
  - **Contact**: Phone number digits only (`^[0-9+]{7,15}$`).
  - **Certificate File**: MIME type limited to `application/pdf`, `image/jpeg`, `image/png`, with maximum size of 10 MB.

---

## 3. Key Product Decisions & Trade-Offs

- **Decision 1: Storage in Dedicated `trainers` Collection**
  - *Chosen Approach*: New top-level `trainers/{trainerId}` collection where `trainerId` matches the user's Auth `uid`.
  - *Why*: Keeps user authentication metadata (`users/{uid}`) isolated from public/administrative professional profiles, simplifies Firestore security rules, and allows high-performance collection queries without polluting member user documents.
- **Decision 2: Certificate Storage Structure**
  - *Chosen Approach*: `trainers/{trainerId}/certificates/{timestamp}_{filename}` in Firebase Storage.
  - *Why*: User-scoped directory enforces ownership in Storage security rules (`request.auth.uid == trainerId || isAdmin()`), preventing trainers from accessing or overwriting each other's documents.
- **Decision 3: Zero Auth Account Creation for Admin "Add Trainer"**
  - *Chosen Approach*: Admin selects or enters an existing user's ID/email to create their trainer profile document.
  - *Why*: Conforms strictly to the constraint: *"Add creates a profile record for an existing user only. Do NOT create a new Firebase Auth account."*

---

## 4. Technical Architecture & Data Strategy

### Component & Data Architecture

```
┌────────────────────────────────────────────────────────┐
│ UI Layer                                               │
│ • TrainerMainScreen (Tab: Account)                     │
│ • TrainerProfileScreen (View / Edit Form)              │
│ • AdminTrainerManagementScreen (Trainer List / Review) │
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│ ViewModel & State Layer                                │
│ • TrainerPortalViewModel (StateFlow<TrainerPortalUiState)
└───────────────────────────┬────────────────────────────┘
                            │
┌───────────────────────────▼────────────────────────────┐
│ Repository Layer                                       │
│ • TrainerPortalRepository                              │
│   ├── Firestore DataSource (trainers/{trainerId})      │
│   └── Firebase Storage Manager (certificates/)         │
└────────────────────────────────────────────────────────┘
```

### Firestore Data Model (`trainers/{trainerId}`)

```json
{
  "trainerId": "string (matches Auth UID)",
  "name": "string (1-80 chars)",
  "email": "string (valid email)",
  "phone": "string (digits only, 7-15 digits)",
  "specialization": "string (free text)",
  "certificateUrl": "string (storage download URL or null)",
  "certificateStoragePath": "string (trainers/{id}/certificates/... or null)",
  "certificateFileName": "string (or null)",
  "certificateMimeType": "string (application/pdf | image/jpeg | image/png)",
  "certificateStatus": "pending | approved | rejected",
  "certificateReviewedBy": "string (admin UID or null)",
  "certificateReviewedAt": "timestamp (or null)",
  "createdAt": "timestamp",
  "updatedAt": "timestamp"
}
```

### Security Rules

#### 1. Firestore Security Rules (`firestore.rules`)

```javascript
// Helper for admin check supporting both role == 'admin' and accountType == 'ADMIN'
function isSystemAdmin() {
  return signedIn() && (
    myRecord().get('role', '').lower() == 'admin' ||
    myRecord().get('accountType', '') == 'ADMIN'
  );
}

function isSystemTrainer() {
  return signedIn() && (
    myRecord().get('role', '').lower() == 'trainer' ||
    myRecord().get('accountType', '') == 'TRAINER'
  );
}

function isValidTrainerProfile(data) {
  return data.keys().hasAll(['trainerId', 'name', 'email', 'phone', 'specialization', 'certificateStatus', 'updatedAt'])
    && data.trainerId is string
    && data.name is string && data.name.size() >= 1 && data.name.size() <= 100
    && data.email is string && data.email.matches('^.+@.+\\..+$')
    && data.phone is string && data.phone.matches('^[0-9+]{7,15}$')
    && data.specialization is string
    && data.certificateStatus in ['pending', 'approved', 'rejected']
    && data.updatedAt is timestamp;
}

match /trainers/{trainerId} {
  // Trainers can read their own profile; Admins can read all profiles. Members have NO access.
  allow read: if isSystemAdmin() || (signedIn() && request.auth.uid == trainerId);
  allow list: if isSystemAdmin();

  // Create: Admin can create for an existing user; Trainer can create their own profile
  allow create: if (isSystemAdmin() || (isSystemTrainer() && request.auth.uid == trainerId))
    && isValidTrainerProfile(request.resource.data)
    && request.resource.data.trainerId == trainerId
    // If trainer creates, certificateStatus must be pending
    && (isSystemAdmin() || request.resource.data.certificateStatus == 'pending');

  // Update:
  // - Admin can update any field including certificateStatus
  // - Trainer can update only their own profile, and ANY certificate update forces certificateStatus to 'pending'
  allow update: if (isSystemAdmin() && isValidTrainerProfile(request.resource.data))
    || (isSystemTrainer()
        && request.auth.uid == trainerId
        && isValidTrainerProfile(request.resource.data)
        // Trainer cannot manually approve/reject
        && (!request.resource.data.diff(resource.data).affectedKeys().hasAny(['certificateStatus'])
            || request.resource.data.certificateStatus == 'pending'));

  // Delete: Admin only
  allow delete: if isSystemAdmin();
}
```

#### 2. Firebase Storage Security Rules

```javascript
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    match /trainers/{trainerId}/certificates/{fileName} {
      // Trainer can read/upload their own certificate; Admin can read all certificates
      allow read: if request.auth != null && (request.auth.uid == trainerId || 
        firestore.get(/databases/(default)/documents/users/$(request.auth.uid)).data.role == 'admin' ||
        firestore.get(/databases/(default)/documents/users/$(request.auth.uid)).data.accountType == 'ADMIN');

      allow write: if request.auth != null 
        && (request.auth.uid == trainerId || 
            firestore.get(/databases/(default)/documents/users/$(request.auth.uid)).data.role == 'admin' ||
            firestore.get(/databases/(default)/documents/users/$(request.auth.uid)).data.accountType == 'ADMIN')
        && request.resource.size < 10 * 1024 * 1024
        && (request.resource.contentType == 'application/pdf'
            || request.resource.contentType == 'image/jpeg'
            || request.resource.contentType == 'image/png');
    }
  }
}
```

---

## 5. Step-by-Step Implementation Sequence

1. **Dependencies & Version Catalog**:
   - Add `libs.firebase.storage` to `gradle/libs.versions.toml` and implement in `app/build.gradle.kts`.
2. **Data Layer**:
   - Create `TrainerProfileDoc` data model in `app/src/main/java/com/example/data/trainer/TrainerProfileModels.kt`.
   - Create `TrainerPortalRepository` in `app/src/main/java/com/example/data/trainer/TrainerPortalRepository.kt` handling Firestore CRUD and Firebase Storage certificate uploads.
3. **Security Rules & Blueprint**:
   - Update `/firestore.rules` and `/firebase-blueprint.json` with the new `/trainers/{trainerId}` schema and role validations.
4. **ViewModel**:
   - Create `TrainerPortalViewModel` with UI state management, validation methods (phone digits, email format, file size/type), and error handling.
5. **UI Components**:
   - Create `TrainerProfileSection.kt` (Trainer profile view/edit form + certificate upload card).
   - Create `AdminTrainerManagementSection.kt` (Admin trainer roster, certificate approval/rejection dialogs, and add trainer dialog).
   - Integrate `TrainerTab.Account` into `TrainerMainScreen.kt` navigation.
6. **Verification & Tests**:
   - Unit tests covering input validation, phone digit enforcement, email parsing, role restrictions, and mock upload state transitions.
   - Run compilation check (`compile_applet`).
