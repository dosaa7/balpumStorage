# 발품 스토리지 사용 안내

발품 스토리지는 REST API를 통해 파일 업로드, 다운로드, 삭제 및 URL 생성 기능을 제공하는 파일 관리 서비스입니다. 이 문서는 발품 스토리지의 주요 기능과 사용 방법에 대해 설명합니다.
발품 스토리지는 Java와 Spring Boot, JPA를 사용하여 개발되었습니다.

## 설치 및 설정

1. **프로젝트 소스 클론**
2. **application 설정**
   application.properties 또는 application.yml 파일에 필요한 설정을 추가합니다.
```application.properties
# DataSource Configuration
spring.datasource.url=jdbc:mysql://<database_host>:<database_port>/<database_name>
spring.datasource.username=<database_username>
spring.datasource.password=<database_password>

# JPA Configuration
spring.jpa.hibernate.ddl-auto=update

# Application Configuration
spring.application.name=balpumStorage

# File Upload Configuration
spring.servlet.multipart.max-file-size=32MB
spring.servlet.multipart.max-request-size=32MB

# Storage Configuration
storage.location=/path/to/storage

# Server Configuration
server.servlet.context-path=/Balpum
```


## 사용 방법

 ## **1. 파일 업로드 (`POST /api/files/`)**
 
 ### **요청 예시**
 
 ```
 httpPOST /api/files/
 Content-Type: multipart/form-data
 
 file=<FILE_TO_UPLOAD>
 ref=<REFERENCE_PATH>
 
 ```
 
 ### **응답**
 
 **201 Created: 파일 업로드 성공메시지: "You successfully uploaded <filename> with reference path <ref>!"**
 
 **400 Bad Request: 파일이 비어 있는 경우메시지: "Please select a file to upload."**
 
 **409 Conflict: 동일한 이름의 파일이 이미 존재하는 경우메시지: "File with the same name already exists."**
 
 **500 Internal Server Error: 기타 예외 발생 시메시지: "Failed to upload <filename>: <ErrorMessage>"**
 
 ## **2. 이미지 URL 반환 (`GET /api/files/image-url`)**
 
 ### **요청 예시**
 
 ```
 httpGET /api/files/image-url
 Content-Type: application/json
 
 {
   "ref": "<REFERENCE_PATH>"
 }
 
 ```
 
 ### **응답**
 
 **200 OK: 파일 URL 반환메시지: "<BASE_URL>/api/files/images/<REF_PATH>"**
 
 ## **3. 이미지 제공 (`GET /api/files/images/**`)**
 
 ### **요청 예시**
 
 ```
 httpGET /api/files/images/<PATH_TO_IMAGE>
 
 ```
 
 ### **응답**
 
 **200 OK: 파일 제공**
 
 **404 Not Found: 파일을 찾을 수 없는 경우**
 
 ## **4. 모든 이미지 URL 리스트 반환 (`GET /api/files/image-url-list`)**
 
 ### **요청 예시**
 
 ```
 httpGET /api/files/image-url-list
 Content-Type: application/json
 
 {
   "ref": "<REFERENCE_PATH>"
 }
 
 ```
 
 ### **응답**
 
 **200 OK: 파일 URL 리스트 반환메시지:
 
 `json
 
 [
   "<BASE_URL>/api/files/images/<FILE_PATH>"
 ]`**
 
 ## **5. 파일 삭제 (`DELETE /api/files/`)**
 
 ### **요청 예시**
 
 ```
 httpDELETE /api/files/
 Content-Type: application/json
 
 {
   "ref": "<REFERENCE_PATH>"
 }
 
 ```
 
 ### **응답**
 
 **204 No Content: 파일 삭제 성공**
 
 **404 Not Found: 파일을 찾을 수 없는 경우**

