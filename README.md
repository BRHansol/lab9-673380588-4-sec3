# Lab 9: Spring Boot - Transaction

> ### เอกสารอ่านประกอบ
>
> สวัดดีครับน้องๆ ตอนนี้เรามาถึง Lab9 แล้ว ถ้าอ่านใบงานนี้แล้วยังไม่เข้าใจว่า Transaction คืออะไร หรือ `@Transactional` ทำงานอย่างไร
> ให้อ่าน **[Study_Notes.md](Study_Notes.md)** ประกอบ เป็นเอกสารที่อธิบายตั้งแต่พื้นฐานครับ


## 1. จุดประสงค์ของ Lab

หลังจากทำ Lab นี้ นักศึกษาจะสามารถ

- สร้าง REST API ด้วย Spring Boot
- แบ่งโครงสร้างโปรแกรมเป็น Model, Service และ Controller
- สร้างความสัมพันธ์ระหว่างตารางแบบ One-to-Many
- ใช้ `@Transactional` ในการควบคุม Transaction
- เข้าใจการทำงานของ COMMIT และ ROLLBACK

---

## 2. โจทย์

ให้นักศึกษาสร้าง **ระบบฝากเงินอย่างง่าย**

ระบบสามารถ

- สร้างบัญชีธนาคาร
- ดูข้อมูลบัญชี
- ฝากเงินเข้าบัญชี
- บันทึกประวัติการฝากเงิน

เมื่อมีการฝากเงิน ระบบต้องทำงาน 2 อย่างพร้อมกัน

1. เพิ่มยอดเงินในบัญชี
2. บันทึกประวัติการฝากเงิน

> ถ้าขั้นตอนใดขั้นตอนหนึ่งเกิดข้อผิดพลาด ข้อมูลทั้งหมดของการฝากเงินครั้งนั้นต้องถูกยกเลิก

---

## 3. เตรียมโปรเจกต์

### 3.1 สร้างโปรเจกต์

สร้างโปรเจกต์ Spring Boot จาก [start.spring.io](https://start.spring.io) โดยเลือก Dependency ดังนี้

| Dependency          | ใช้ทำอะไร                          |
| ------------------- | --------------------------------- |
| Spring Web          | สร้าง REST API                     |
| Spring Data JPA     | ติดต่อฐานข้อมูลและจัดการ Transaction   |
| PostgreSQL Driver   | ตัวเชื่อมต่อกับฐานข้อมูล PostgreSQL     |

### 3.2 เตรียมฐานข้อมูล

เปิด pgAdmin หรือ psql แล้วสร้าง Database สำหรับ Lab นี้ก่อน

```sql
CREATE DATABASE lab9;
```

### 3.3 ตั้งค่า application.properties

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/lab9
spring.datasource.username=postgres
spring.datasource.password=YOUR_PASSWORD_HERE

spring.jpa.show-sql=true
spring.jpa.hibernate.ddl-auto=update
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
```

> **สำคัญ:** ให้เปลี่ยน `YOUR_PASSWORD_HERE` เป็นรหัสผ่านของ PostgreSQL ที่ตั้งไว้ตอนติดตั้ง
> และถ้าใช้ port อื่นที่ไม่ใช่ `5432` ให้แก้ในบรรทัด `spring.datasource.url` ด้วย

> **หมายเหตุ:** ข้อมูลใน PostgreSQL จะยังอยู่แม้ปิดโปรแกรม
> ดังนั้นถ้าทดลองซ้ำหลายรอบ ควรตรวจดูข้อมูลเดิมในตารางก่อน
> เพื่อไม่ให้สับสนกับผลการทดลองรอบใหม่

---

## 4. Database

ระบบมีทั้งหมด 2 ตาราง

```text
Account
        1
        |
        |
        *
DepositTransaction
```

หมายความว่า 1 Account สามารถมี DepositTransaction ได้หลายรายการ

---

## 5. โครงสร้าง Project

ให้สร้าง Project ตามโครงสร้างดังนี้

```text
src/main/java/com/example/lab9
│
├── controller
│   └── AccountController.java
│
├── service
│   ├── AccountService.java
│   └── DepositService.java
│
├── repository
│   ├── AccountRepository.java
│   └── DepositRepository.java
│
└── model
    ├── Account.java
    └── DepositTransaction.java
```

---

## 6. Model

ให้สร้าง Entity จำนวน 2 ตัว ได้แก่ `Account` และ `DepositTransaction`

### 6.1 Account

| Column          | Type   | Key | Description      |
| --------------- | ------ | --- | ---------------- |
| `id`            | Long   | PK  | รหัสบัญชี          |
| `accountNumber` | String | -   | เลขบัญชี           |
| `ownerName`     | String | -   | ชื่อเจ้าของบัญชี     |
| `balance`       | Double | -   | ยอดเงิน           |

### 6.2 DepositTransaction

| Column       | Type   | Key | Description       |
| ------------ | ------ | --- | ----------------- |
| `id`         | Long   | PK  | รหัสรายการฝาก       |
| `amount`     | Double | -   | จำนวนเงินที่ฝาก      |
| `account_id` | Long   | FK  | อ้างอิง Account     |

### 6.3 ความสัมพันธ์

```text
Account 1 ---- * DepositTransaction
```

ความสัมพันธ์นี้ให้เขียนไว้ที่ **ฝั่ง `DepositTransaction` เท่านั้น**

- ใน `DepositTransaction` ให้มีฟิลด์ที่อ้างถึง `Account` และกำหนดให้เป็นความสัมพันธ์แบบ Many-to-One
  โดยตั้งชื่อคอลัมน์ Foreign Key เป็น `account_id`
- ใน `Account` **ไม่ต้องมีฟิลด์ที่เก็บลิสต์ของ DepositTransaction**

> **ทำไมไม่ต้องใส่ฝั่ง Account?**
>
> เพราะถ้าใส่ทั้งสองฝั่ง แล้วส่งข้อมูลออกเป็น JSON ตอนเรียก `GET /accounts/{id}`
> ระบบจะวนอ่านข้อมูลไปมาไม่รู้จบ (Account → รายการฝาก → Account → รายการฝาก → ...)
> จนเกิด Error ทันที
>
> ความสัมพันธ์แบบ One-to-Many ในฐานข้อมูลถูกกำหนดด้วยคอลัมน์ Foreign Key ฝั่ง Many อยู่แล้ว
> การใส่ฝั่งเดียวจึงเพียงพอสำหรับ Lab นี้

---

## 7. Service

Service มีหน้าที่จัดการ Business Logic ของระบบ

ใน Lab นี้ให้สร้าง 2 ไฟล์ ในโฟลเดอร์ `service`

- `AccountService.java`
- `DepositService.java`

### AccountService

ให้สร้างเมธอดสำหรับ

- สร้าง Account ใหม่
- ค้นหา Account จาก id

### DepositService

ให้สร้างเมธอด

```text
deposit(accountId, amount)
```

โดยให้ทำงานตามลำดับ

1. ค้นหา Account จาก accountId
2. เพิ่มจำนวนเงินเข้า balance แล้วบันทึก Account
3. สร้าง DepositTransaction ผูกกับ Account นั้น แล้วบันทึกลง Database

> **เมธอด `deposit()` นี้คือเมธอดเดียวกับที่จะใส่ `@Transactional` ในข้อ 8**
> ตอนนี้ให้เขียนตรรกะทั้ง 3 ขั้นตอนให้เสร็จก่อน แล้วค่อยไปเติม annotation ในข้อถัดไป

---

## 8. Transaction

### `@Transactional` เขียนไว้ที่ไหน?

**เขียนที่ชั้น Service** — คือไฟล์ `DepositService.java` ที่สร้างไว้ในข้อ 7
โดยเติมไว้เหนือเมธอด `deposit()` ที่เพิ่งเขียนเสร็จ

ไม่ต้องสร้างไฟล์ใหม่ ไม่ต้องเขียนเมธอดใหม่ **เป็นเมธอดตัวเดิมจากข้อ 7**
เพียงแต่เติมบรรทัด `@Transactional` เข้าไปข้างบนเท่านั้น

### ทำไมต้องเป็น Service?

ลองดูว่าแต่ละชั้นมองเห็นอะไรบ้าง

```text
Controller  →  รับ Request จาก Postman เท่านั้น
                ไม่รู้เรื่องฐานข้อมูล

Service     →  รู้ว่า "ฝากเงิน 1 ครั้ง" ต้องทำอะไรบ้าง
                ★ @Transactional อยู่ตรงนี้ ★

Repository  →  เห็นแค่คำสั่งเดียว เช่น save()
                ไม่รู้ว่าตัวเองเป็นส่วนหนึ่งของงานใหญ่แค่ไหน
```

Service เป็นชั้นเดียวที่รู้ว่างานหนึ่งชิ้นเริ่มตรงไหนและจบตรงไหน
จึงเป็นชั้นเดียวที่กำหนดขอบเขตของ Transaction ได้

### หน้าตาของโค้ดหลังเติม `@Transactional`

ไฟล์ `DepositService.java`

```java
@Transactional
public void deposit(Long accountId, Double amount) {

    // ค้นหา Account

    // เพิ่ม balance

    // สร้าง DepositTransaction

}
```

บรรทัด `@Transactional` ที่เติมเข้าไปมีความหมายว่า
**"ทุกคำสั่งในเมธอดนี้ นับเป็นงานก้อนเดียวกัน"**

### การทำงาน

```text
เริ่ม Transaction
       |
       v
ค้นหา Account
       |
       v
เพิ่ม balance
       |
       v
บันทึก DepositTransaction
       |
       v
     สำเร็จ
       |
       v
    COMMIT
```

### ถ้าเกิด Error

```text
เริ่ม Transaction
       |
       v
ค้นหา Account
       |
       v
เพิ่ม balance
       |
       v
เกิด Error
       |
       v
    ROLLBACK
```

---

## 9. ทำไมต้องใช้ `@Transactional`?

การฝากเงิน 1 ครั้งมีการเปลี่ยนแปลงข้อมูลมากกว่า 1 อย่าง

```text
ฝากเงิน
  |
  +----> เพิ่ม balance
  |
  +----> บันทึก DepositTransaction
```

ทั้งสองขั้นตอนควรสำเร็จพร้อมกัน

ถ้าเกิด Error ระบบควรย้อนกลับการเปลี่ยนแปลงทั้งหมด

---

## 10. Controller

ให้สร้าง Controller เพียงตัวเดียว คือ `AccountController.java`

โดยต้องมี Endpoint สำหรับ

```http
POST /accounts
GET  /accounts/{id}
POST /accounts/{id}/deposit
```

Controller มีหน้าที่รับ Request จาก Client และเรียกใช้งาน Service

> **ข้อสังเกต:** ทั้ง 3 endpoint ขึ้นต้นด้วย `/accounts` เหมือนกัน
> ถือว่าเป็นข้อมูลชุดเดียวกัน จึงรวมไว้ในคลาสเดียวได้
>
> Controller ตัวนี้จะเรียกใช้ **2 Service** คือ
>
> - `AccountService` สำหรับ `POST /accounts` และ `GET /accounts/{id}`
> - `DepositService` สำหรับ `POST /accounts/{id}/deposit` (ตัวที่มี `@Transactional`)
>
> ให้รับทั้งสอง Service เข้ามาผ่าน constructor เดียวกัน
>
> สองคลาสนี้ใช้ path ขึ้นต้นเหมือนกันได้ ไม่ชนกัน เพราะ Spring แยกด้วย path เต็มและ HTTP Method
> ให้กำหนด path ของแต่ละเมธอดให้ครบและไม่ซ้ำกัน

---

## 11. ทดลองใช้งานด้วย Postman

ให้ใช้ **Postman** ทดสอบ API ทั้ง 3 เส้น

### 11.0 เตรียม Postman

รันโปรแกรมด้วยคำสั่ง `./mvnw spring-boot:run` รอจนขึ้นข้อความ `Started Lab9Application`
จากนั้นเปิด Postman แล้วสร้าง Collection ใหม่ชื่อ `Lab9` เพื่อเก็บ Request ทั้ง 3 ตัวไว้ด้วยกัน
จะได้กดยิงซ้ำได้ง่ายเวลาทดลองหลายรอบ

---

การเขียนโค้ดมีหลายวิธีนะครับ ตัวอย่างด้านล่างเป็นวิธีเขียน Transaction ใน DepositService.java

ถ้าใครเขียนออกมาไม่เหมือนตัวอย่างก็ไม่เป็นไรครับ ขอแค่หลักการทำงานเหมือนกัน สามารถเอาตัวอย่างนี้ไปปรับใช้กับโค้ดของตัวเองได้เลยครับ

![ตัวอย่างTransaction @Transactional](picture/A.png)

### 11.1 สร้าง Account

ตั้งค่า Request ใน Postman ดังนี้

| ช่อง       | ค่าที่ใส่                            |
| --------- | --------------------------------- |
| Method    | `POST`                            |
| URL       | `http://localhost:8080/accounts`  |
| Body      | `raw` → `JSON`                    |

ข้อมูลที่ใส่ในช่อง Body

```json
{
    "accountNumber": "1234567890",
    "ownerName": "ใส่ชื่อตัวเองนะ",
    "balance": 0
}
```

กด **Send** ควรได้ Status `200 OK` และ Response หน้าตาแบบนี้

```json
{
    "accountNumber": "1234567890",
    "ownerName": "John",
    "balance": 0.0,
    "id": 1
}
```

> **จดเลข `id` ที่ได้ไว้** ปกติจะเป็น `1`
> ถ้าได้เลขอื่น ให้ใช้เลขนั้นแทน `1` ใน URL ของข้อ 11.2 และ 11.3

> **สิ่งที่ต้องแคป**
>
> แคปหน้าจอ Response จาก Postman ที่แสดงว่า Account ถูกสร้างสำเร็จ และมี id ของ Account

### 11.2 ฝากเงิน

| ช่อง       | ค่าที่ใส่                                     |
| --------- | ------------------------------------------ |
| Method    | `POST`                                     |
| URL       | `http://localhost:8080/accounts/1/deposit` |
| Body      | `raw` → `JSON`                             |

ข้อมูลที่ใส่ในช่อง Body

```json
{
    "amount": 1000
}
```

กด **Send** ควรได้ Status `200 OK` และ Response

```json
{
    "message": "Deposit successful"
}
```

> **สิ่งที่ต้องแคป**
>
> แคปหน้าจอ Request และ Response จาก Postman ที่แสดงว่าการฝากเงินสำเร็จ
> (ให้เห็นทั้งช่อง Body ที่ส่งไป และ Response ที่ได้กลับมา)

### 11.3 ตรวจสอบยอดเงิน

| ช่อง       | ค่าที่ใส่                              |
| --------- | ----------------------------------- |
| Method    | `GET`                               |
| URL       | `http://localhost:8080/accounts/1`  |
| Body      | **ไม่ต้องใส่**                        |

กด **Send** ควรพบว่า `balance` เปลี่ยนจาก `0` เป็น `1000`

```json
{
    "accountNumber": "1234567890",
    "ownerName": "John",
    "balance": 1000.0,
    "id": 1
}
```

> **สิ่งที่ต้องแคป**
>
> แคปหน้าจอ Response ที่แสดงข้อมูล Account และ balance = 1000

### 11.4 ตรวจสอบประวัติการฝากเงิน

ตารางนี้**ไม่มี endpoint ให้เรียกผ่าน Postman** ต้องเปิดดูใน pgAdmin

เปิด pgAdmin เชื่อมต่อไปที่ Database `lab9` แล้วรันคำสั่ง

```sql
SELECT * FROM deposit_transaction;
```

ควรพบ 1 แถว หน้าตาประมาณนี้

```text
 id | amount | account_id
----+--------+------------
  1 |   1000 |          1
```

> **สิ่งที่ต้องแคป**
>
> แคปหน้าจอ ตาราง `deposit_transaction` ใน pgAdmin ที่แสดงรายการฝากเงิน

---

## 12. ทดลอง Transaction Rollback

ข้อนี้คือการจงใจทำให้เกิด Error หลังจากบันทึกข้อมูลไปแล้ว เพื่อดูว่า `@Transactional` ย้อนข้อมูลกลับให้หรือไม่

### 12.1 แก้โค้ดในไฟล์ `DepositService.java`

ให้เพิ่มบรรทัดนี้ไว้ที่ **บรรทัดสุดท้ายของเมธอด `deposit()`**

```java
throw new RuntimeException("Test Rollback");
```

โค้ดทั้งเมธอดจะกลายเป็นแบบนี้ (น้องสามารถทดลองใส่ Error จุดต่างๆเพื่อทดสอบการทำงานของ @Transactional ได้นะครับ)

![ตัวอย่างการThrowError @Transactional](picture/AddThrow.jpg)

> **สังเกตตำแหน่งของบรรทัดที่โยน Error**
>
> อยู่ **หลังจากสั่งบันทึกข้อมูลครบทั้งสองอย่างแล้ว** เพื่อจำลองสถานการณ์ที่งานทำไปจนเกือบเสร็จ
> แล้วมาพังเอาตอนท้าย ถ้าใส่ไว้บรรทัดแรกจะไม่ได้พิสูจน์อะไรเลย

> **ห้ามลืม:** ต้อง **หยุดโปรแกรมแล้วรันใหม่** ทุกครั้งที่แก้โค้ด
> (กด `Ctrl+C` แล้วสั่ง `./mvnw spring-boot:run` ใหม่)
> ไม่งั้น Postman จะยังยิงไปเจอโค้ดตัวเก่าอยู่

### 12.2 ยิง Request ซ้ำด้วย Postman

ใช้ Request เดิมจากข้อ 11.2 ได้เลย ไม่ต้องสร้างใหม่

| ช่อง       | ค่าที่ใส่                                     |
| --------- | ------------------------------------------ |
| Method    | `POST`                                     |
| URL       | `http://localhost:8080/accounts/1/deposit` |
| Body      | `raw` → `JSON`                             |

ข้อมูลที่ใส่ในช่อง Body (เหมือนเดิมทุกอย่าง)

```json
{
    "amount": 1000
}
```

กด **Send** คราวนี้ต้องได้ Status **`500 Internal Server Error`**

> **สิ่งที่ต้องแคป (รูปที่ 1 ของข้อนี้)**
>
> แคปหน้าจอ Postman ที่แสดง Status 500

### 12.3 ตรวจสอบว่าข้อมูลถูกย้อนกลับหรือไม่

ต้องตรวจ **2 ที่** ทั้งที่ Postman และ pgAdmin

**ที่ 1 — ยอดเงินใน Account** ใช้ Request เดิมจากข้อ 11.3

| ช่อง       | ค่าที่ใส่                              |
| --------- | ----------------------------------- |
| Method    | `GET`                               |
| URL       | `http://localhost:8080/accounts/1`  |

กด **Send** แล้วดูค่า `balance`

```text
ต้องยังเป็น 1000.0 เท่าเดิม  ←  ถูกต้อง (rollback ทำงาน)
ถ้าเป็น 2000.0              ←  ผิด ให้ตรวจว่าใส่ @Transactional ไว้หรือยัง
```

**ที่ 2 — ตาราง `deposit_transaction`** เปิด pgAdmin แล้วรัน

```sql
SELECT * FROM deposit_transaction;
```

```text
ต้องยังมีแค่ 1 แถวเท่าเดิม  ←  ถูกต้อง (rollback ทำงาน)
ถ้ามี 2 แถว                ←  ผิด ข้อมูลถูกบันทึกจริง
```

> **สิ่งที่ต้องแคป (รูปที่ 2 ของข้อนี้)**
>
> - Response จาก Postman ที่แสดงว่า `balance` ไม่เพิ่ม
> - ตาราง `deposit_transaction` ใน pgAdmin ที่แสดงว่าไม่มีรายการใหม่

### 12.4 สรุปผล

ทั้งที่โค้ดสั่ง `save()` ไปแล้วทั้งสองอย่างก่อนจะเกิด Error
แต่ไม่มีอะไรถูกบันทึกลงฐานข้อมูลจริงเลย เพราะ `@Transactional` สั่ง **ROLLBACK** ให้ทั้งก้อน

> **บรรทัดที่ต้อง comment ทีหลัง**
>
> บรรทัด `throw new RuntimeException("Test Rollback");` ยัง**ต้องเปิดไว้**เพื่อใช้ทดลองข้อ 13 ต่อ
> จะ comment ทิ้งก็ต่อเมื่อทำข้อ 13 เสร็จแล้ว ตามที่ระบุไว้ในหมายเหตุท้ายเอกสาร

---

## 13. ทดลองโดยเอา `@Transactional` ออก

![ตัวอย่างการปิด @Transactional](picture/removeTransaction1.jpg)

ให้ลบ

```java
@Transactional
```

ออกจาก `deposit()` โดย**ยังคงบรรทัด `throw new RuntimeException("Test Rollback");` ไว้เหมือนเดิม**

จากนั้นทดลองฝากเงินอีกครั้ง แล้วตรวจสอบข้อมูลใน Database

ให้นักศึกษาสังเกตความแตกต่างระหว่าง

```text
มี @Transactional
        ↓
เกิด Error
        ↓
ROLLBACK
        ↓
ข้อมูลกลับไปเหมือนเดิม ไม่มีอะไรถูกบันทึก
```

และ

```text
ไม่มี @Transactional
        ↓
เกิด Error
        ↓
ไม่มี ROLLBACK
        ↓
ข้อมูลที่บันทึกไปแล้ว ยังคงอยู่ใน Database
```

> **เหตุผล:** เมื่อไม่มี `@Transactional` คำสั่ง `save()` แต่ละครั้งจะบันทึกลงฐานข้อมูลจบไปทีละคำสั่ง
> ไม่มีใครมองภาพรวมว่างานทั้งก้อนสำเร็จหรือไม่ พอเกิด Error ทีหลัง จึงไม่มีอะไรย้อนกลับให้

> **สิ่งที่ต้องแคป**
>
> แคปผลการทดลองที่แสดงความแตกต่างระหว่าง
>
> - ตอนที่มี `@Transactional`
> - ตอนที่ไม่มี `@Transactional`



---

## 14. คำถามท้าย Lab

1. `@Transactional` มีหน้าที่อะไร?
2. เพราะเหตุใดการฝากเงินจึงควรใช้ Transaction?
3. COMMIT และ ROLLBACK ต่างกันอย่างไร?
4. Account และ DepositTransaction มีความสัมพันธ์แบบใด?
5. เมื่อเกิด Error ขณะใช้ `@Transactional` ข้อมูลใน Database เปลี่ยนแปลงอย่างไร?
6. จากการทดลอง เมื่อลบ `@Transactional` ออก ผลลัพธ์แตกต่างจากเดิมอย่างไร?

---

## 15. สิ่งที่ต้องส่ง อัพขึ้น github ของตนเอง แล้วส่ง link ใน classroom

- Source Code ของ Spring Boot Project
- ไฟล์ Word ที่นำภาพจากการทดลองมาใส่ พร้อมอธิบายผลการทดลอง

ใน Word ให้เรียงเนื้อหาโดยประมาณดังนี้

```text
1. การสร้าง Account
   - รูปภาพ
   - อธิบายผล

2. การฝากเงินสำเร็จ
   - รูปภาพ
   - อธิบายผล

3. ตรวจสอบ Account
   - รูปภาพ
   - อธิบายผล

4. ตรวจสอบ DepositTransaction
   - รูปภาพ
   - อธิบายผล

5. ทดลอง Rollback
   - รูปภาพ
   - อธิบายผล

6. เปรียบเทียบมี @Transactional กับไม่มี @Transactional
   - รูปภาพ
   - อธิบายผล

7. ตอบคำถามท้าย Lab ทั้ง 6 ข้อ
```

> **หมายเหตุ:** หลังจากทดลอง Rollback เสร็จแล้ว ให้ลบ `throw new RuntimeException("Test Rollback");` ออก
> และใส่ `@Transactional` กลับคืน เพื่อให้โปรแกรมกลับมาทำงานตามปกติ
