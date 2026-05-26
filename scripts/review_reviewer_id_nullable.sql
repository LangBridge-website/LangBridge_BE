-- 번역 완료 시 PENDING 리뷰는 reviewer 미할당(null). 승인/반려 시점에 reviewer_id 설정.
-- MariaDB / MySQL. FK 이름은 SHOW CREATE TABLE review; 로 확인 후 필요 시 수정.

ALTER TABLE review
    MODIFY COLUMN reviewer_id BIGINT NULL;
