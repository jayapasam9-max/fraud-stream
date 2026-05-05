# Healthcare Mode — Claims Fraud

The same pipeline runs over insurance claims by swapping the input topic schema and rule configuration. No code changes to the rule engine — only new rule implementations.

## Mapping

| Concept | Banking | Healthcare |
|---------|---------|------------|
| Event | Credit card transaction | Insurance claim line item |
| Entity | `card_id` / `account_id` | `provider_npi` / `member_id` |
| Amount | Transaction amount | Billed amount |
| Merchant | MCC code | CPT / HCPCS code |
| Velocity | Tx per card per minute | Claims per provider per day |

## Healthcare-equivalent rules

**`ProviderVelocityRule`** — A provider submitting more than N claims per day for distinct members beyond their historical baseline. Direct analog of `VelocityRule`.

**`UpcodingPatternRule`** — Distribution of CPT codes for a provider skews toward higher-reimbursement codes versus peers in the same specialty. Replaces `AmountDeviationRule`.

**`DuplicateBillingRule`** — Same member, same CPT, same date of service, multiple submissions. Replaces `RoundAmountBurstRule`.

**`ImpossibleVisitRule`** — Two in-person claims for the same provider in different facilities within an impossible time window. Direct analog of `GeoImpossibleRule`.

**`UnbundlingRule`** — Submission of component CPT codes that should have been billed as a single bundled code per CMS National Correct Coding Initiative (NCCI). New rule, no banking analog.

## Switching modes

The default profile is `banking`. To run in healthcare mode:

```bash
SPRING_PROFILES_ACTIVE=healthcare ./mvnw spring-boot:run
```

This loads `application-healthcare.yml`, which:
- Subscribes to the `claims` topic instead of `transactions`
- Activates the healthcare rule beans (annotated `@Profile("healthcare")`)
- Uses the `claims` deserializer

A sample claims dataset can be generated with `scripts/generate_claims.py` (mirrors `generate_data.py` but produces CMS-1500-style line items with injected fraud patterns).

## Why this matters

Payers like Cigna, UnitedHealth, and Anthem run claims fraud detection systems that look architecturally identical to bank fraud detection: high-volume event stream, windowed aggregations, a hybrid rules + ML scoring layer, and a reviewer queue. The skills transfer directly. This project demonstrates that transferability with a single codebase.
