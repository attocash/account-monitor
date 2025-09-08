CREATE TABLE account
(
  address      VARCHAR(61)                               NOT NULL PRIMARY KEY,
  version      INT UNSIGNED                                    NOT NULl,
  persisted_at TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) NOT NULL,
  updated_at   TIMESTAMP(6) DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) NOT NULL,
  disabled_at  TIMESTAMP(6) NULL
);

CREATE TABLE account_entry
(
  hash               VARBINARY(32) PRIMARY KEY,

  algorithm          ENUM ('V1')                                                              NOT NULL,
  public_key         VARBINARY(32)                                                            NOT NULL,
  height             BIGINT UNSIGNED                                                          NOT NULL,
  block_type         ENUM ('OPEN','SEND','RECEIVE','CHANGE')                                  NOT NULL,
  subject_algorithm  ENUM ('V1')                                                              NOT NULL,
  subject_public_key VARBINARY(32)                                                            NOT NULL,

  previous_balance   BIGINT UNSIGNED                                                          NOT NULL,
  balance            BIGINT UNSIGNED                                                          NOT NULL,
  timestamp          TIMESTAMP(3)                              NOT NULL,


  persisted_at       TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3) NOT NULL,
  updated_at         TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) NOT NULL
);

CREATE UNIQUE INDEX account_entry_public_key_height ON account_entry (public_key, height);
CREATE INDEX account_entry_persisted_at ON account_entry (persisted_at);

CREATE TABLE transaction
(
  hash         VARBINARY(32) PRIMARY KEY,

  algorithm    ENUM ('V1')                                NOT NULL,
  public_key   VARBINARY(32)                              NOT NULL,
  height       BIGINT UNSIGNED                            NOT NULL,

  serialized   VARBINARY(206)                             NOT NULL,

  received_at  TIMESTAMP(3)                              NOT NULL,
  persisted_at TIMESTAMP(3) DEFAULT CURRENT_TIMESTAMP(3) NOT NULL
);

CREATE UNIQUE INDEX transaction_public_key_height ON transaction (public_key, height);
CREATE INDEX transaction_persisted_at ON transaction (persisted_at);
