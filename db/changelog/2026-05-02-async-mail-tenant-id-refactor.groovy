databaseChangeLog = {

    // -------------------------------------------------------------
    // Sequences for the new synthetic primary keys
    // -------------------------------------------------------------
    changeSet(author: "Deepak Goyal", id: "createAsyncMailToIdSeq") {
        preConditions(onFail: 'MARK_RAN') {
            not {
                sequenceExists(sequenceName: "ASYNC_MAIL_TO_ID_SEQ")
            }
        }
        createSequence(sequenceName: "ASYNC_MAIL_TO_ID_SEQ",
                startValue: 1, incrementBy: 1, cacheSize: 20, cycle: false)
    }

    changeSet(author: "Deepak Goyal", id: "createAsyncMailBccIdSeq") {
        preConditions(onFail: 'MARK_RAN') {
            not {
                sequenceExists(sequenceName: "ASYNC_MAIL_BCC_ID_SEQ")
            }
        }
        createSequence(sequenceName: "ASYNC_MAIL_BCC_ID_SEQ",
                startValue: 1, incrementBy: 1, cacheSize: 20, cycle: false)
    }

    changeSet(author: "Deepak Goyal", id: "createAsyncMailCcIdSeq") {
        preConditions(onFail: 'MARK_RAN') {
            not {
                sequenceExists(sequenceName: "ASYNC_MAIL_CC_ID_SEQ")
            }
        }
        createSequence(sequenceName: "ASYNC_MAIL_CC_ID_SEQ",
                startValue: 1, incrementBy: 1, cacheSize: 20, cycle: false)
    }

    changeSet(author: "Deepak Goyal", id: "createAsyncMailHeaderIdSeq") {
        preConditions(onFail: 'MARK_RAN') {
            not {
                sequenceExists(sequenceName: "ASYNC_MAIL_HEADER_ID_SEQ")
            }
        }
        createSequence(sequenceName: "ASYNC_MAIL_HEADER_ID_SEQ",
                startValue: 1, incrementBy: 1, cacheSize: 20, cycle: false)
    }

    // -------------------------------------------------------------
    // ASYNC_MAIL_TO
    // -------------------------------------------------------------
    changeSet(author: "Deepak Goyal", id: "asyncMailToIdColumnAdded") {
        preConditions(onFail: 'MARK_RAN') {
            not {
                columnExists(tableName: 'ASYNC_MAIL_TO', columnName: 'ID')
            }
        }
        addColumn(tableName: "ASYNC_MAIL_TO") {
            column(name: "ID", type: "NUMBER(19)") {
                constraints(nullable: "true")
            }
        }
    }

    changeSet(author: "Deepak Goyal", id: "asyncMailToIdBackfilled") {
        preConditions(onFail: 'MARK_RAN') {
            sqlCheck(expectedResult: "Y",
                    "SELECT CASE WHEN EXISTS (SELECT 1 FROM ASYNC_MAIL_TO WHERE ID IS NULL) THEN 'Y' ELSE 'N' END FROM DUAL")
        }
        sql "UPDATE ASYNC_MAIL_TO SET ID = ASYNC_MAIL_TO_ID_SEQ.NEXTVAL WHERE ID IS NULL"
    }

    changeSet(author: "Deepak Goyal", id: "asyncMailToIdNotNull") {
        preConditions(onFail: 'MARK_RAN') {
            sqlCheck(expectedResult: "Y",
                    "SELECT NULLABLE FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'ASYNC_MAIL_TO' AND COLUMN_NAME = 'ID'")
        }
        addNotNullConstraint(tableName: "ASYNC_MAIL_TO", columnName: "ID", columnDataType: "NUMBER(19)")
    }

    changeSet(author: "Deepak Goyal", id: "asyncMailToPkAdded") {
        preConditions(onFail: 'MARK_RAN') {
            not {
                primaryKeyExists(tableName: "ASYNC_MAIL_TO", primaryKeyName: "PK_ASYNC_MAIL_TO")
            }
        }
        addPrimaryKey(tableName: "ASYNC_MAIL_TO", columnNames: "ID", constraintName: "PK_ASYNC_MAIL_TO")
    }

    changeSet(author: "Deepak Goyal", id: "asyncMailToMsgIdxAdded") {
        preConditions(onFail: 'MARK_RAN') {
            not {
                indexExists(indexName: "IDX_ASYNC_MAIL_TO_MSG")
            }
        }
        createIndex(tableName: "ASYNC_MAIL_TO", indexName: "IDX_ASYNC_MAIL_TO_MSG") {
            column(name: "MESSAGE_ID")
        }
    }

    // -------------------------------------------------------------
    // ASYNC_MAIL_BCC
    // -------------------------------------------------------------
    changeSet(author: "Deepak Goyal", id: "asyncMailBccIdColumnAdded") {
        preConditions(onFail: 'MARK_RAN') {
            not {
                columnExists(tableName: 'ASYNC_MAIL_BCC', columnName: 'ID')
            }
        }
        addColumn(tableName: "ASYNC_MAIL_BCC") {
            column(name: "ID", type: "NUMBER(19)") {
                constraints(nullable: "true")
            }
        }
    }

    changeSet(author: "Deepak Goyal", id: "asyncMailBccIdBackfilled") {
        preConditions(onFail: 'MARK_RAN') {
            sqlCheck(expectedResult: "Y",
                    "SELECT CASE WHEN EXISTS (SELECT 1 FROM ASYNC_MAIL_BCC WHERE ID IS NULL) THEN 'Y' ELSE 'N' END FROM DUAL")
        }
        sql "UPDATE ASYNC_MAIL_BCC SET ID = ASYNC_MAIL_BCC_ID_SEQ.NEXTVAL WHERE ID IS NULL"
    }

    changeSet(author: "Deepak Goyal", id: "asyncMailBccIdNotNull") {
        preConditions(onFail: 'MARK_RAN') {
            sqlCheck(expectedResult: "Y",
                    "SELECT NULLABLE FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'ASYNC_MAIL_BCC' AND COLUMN_NAME = 'ID'")
        }
        addNotNullConstraint(tableName: "ASYNC_MAIL_BCC", columnName: "ID", columnDataType: "NUMBER(19)")
    }

    changeSet(author: "Deepak Goyal", id: "asyncMailBccPkAdded") {
        preConditions(onFail: 'MARK_RAN') {
            not {
                primaryKeyExists(tableName: "ASYNC_MAIL_BCC", primaryKeyName: "PK_ASYNC_MAIL_BCC")
            }
        }
        addPrimaryKey(tableName: "ASYNC_MAIL_BCC", columnNames: "ID", constraintName: "PK_ASYNC_MAIL_BCC")
    }

    changeSet(author: "Deepak Goyal", id: "asyncMailBccMsgIdxAdded") {
        preConditions(onFail: 'MARK_RAN') {
            not {
                indexExists(indexName: "IDX_ASYNC_MAIL_BCC_MSG")
            }
        }
        createIndex(tableName: "ASYNC_MAIL_BCC", indexName: "IDX_ASYNC_MAIL_BCC_MSG") {
            column(name: "MESSAGE_ID")
        }
    }

    // -------------------------------------------------------------
    // ASYNC_MAIL_CC
    // -------------------------------------------------------------
    changeSet(author: "Deepak Goyal", id: "asyncMailCcIdColumnAdded") {
        preConditions(onFail: 'MARK_RAN') {
            not {
                columnExists(tableName: 'ASYNC_MAIL_CC', columnName: 'ID')
            }
        }
        addColumn(tableName: "ASYNC_MAIL_CC") {
            column(name: "ID", type: "NUMBER(19)") {
                constraints(nullable: "true")
            }
        }
    }

    changeSet(author: "Deepak Goyal", id: "asyncMailCcIdBackfilled") {
        preConditions(onFail: 'MARK_RAN') {
            sqlCheck(expectedResult: "Y",
                    "SELECT CASE WHEN EXISTS (SELECT 1 FROM ASYNC_MAIL_CC WHERE ID IS NULL) THEN 'Y' ELSE 'N' END FROM DUAL")
        }
        sql "UPDATE ASYNC_MAIL_CC SET ID = ASYNC_MAIL_CC_ID_SEQ.NEXTVAL WHERE ID IS NULL"
    }

    changeSet(author: "Deepak Goyal", id: "asyncMailCcIdNotNull") {
        preConditions(onFail: 'MARK_RAN') {
            sqlCheck(expectedResult: "Y",
                    "SELECT NULLABLE FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'ASYNC_MAIL_CC' AND COLUMN_NAME = 'ID'")
        }
        addNotNullConstraint(tableName: "ASYNC_MAIL_CC", columnName: "ID", columnDataType: "NUMBER(19)")
    }

    changeSet(author: "Deepak Goyal", id: "asyncMailCcPkAdded") {
        preConditions(onFail: 'MARK_RAN') {
            not {
                primaryKeyExists(tableName: "ASYNC_MAIL_CC", primaryKeyName: "PK_ASYNC_MAIL_CC")
            }
        }
        addPrimaryKey(tableName: "ASYNC_MAIL_CC", columnNames: "ID", constraintName: "PK_ASYNC_MAIL_CC")
    }

    changeSet(author: "Deepak Goyal", id: "asyncMailCcMsgIdxAdded") {
        preConditions(onFail: 'MARK_RAN') {
            not {
                indexExists(indexName: "IDX_ASYNC_MAIL_CC_MSG")
            }
        }
        createIndex(tableName: "ASYNC_MAIL_CC", indexName: "IDX_ASYNC_MAIL_CC_MSG") {
            column(name: "MESSAGE_ID")
        }
    }

    // -------------------------------------------------------------
    // ASYNC_MAIL_HEADER
    // -------------------------------------------------------------
    changeSet(author: "Deepak Goyal", id: "asyncMailHeaderIdColumnAdded") {
        preConditions(onFail: 'MARK_RAN') {
            not {
                columnExists(tableName: 'ASYNC_MAIL_HEADER', columnName: 'ID')
            }
        }
        addColumn(tableName: "ASYNC_MAIL_HEADER") {
            column(name: "ID", type: "NUMBER(19)") {
                constraints(nullable: "true")
            }
        }
    }

    changeSet(author: "Deepak Goyal", id: "asyncMailHeaderIdBackfilled") {
        preConditions(onFail: 'MARK_RAN') {
            sqlCheck(expectedResult: "Y",
                    "SELECT CASE WHEN EXISTS (SELECT 1 FROM ASYNC_MAIL_HEADER WHERE ID IS NULL) THEN 'Y' ELSE 'N' END FROM DUAL")
        }
        sql "UPDATE ASYNC_MAIL_HEADER SET ID = ASYNC_MAIL_HEADER_ID_SEQ.NEXTVAL WHERE ID IS NULL"
    }

    changeSet(author: "Deepak Goyal", id: "asyncMailHeaderIdNotNull") {
        preConditions(onFail: 'MARK_RAN') {
            sqlCheck(expectedResult: "Y",
                    "SELECT NULLABLE FROM USER_TAB_COLUMNS WHERE TABLE_NAME = 'ASYNC_MAIL_HEADER' AND COLUMN_NAME = 'ID'")
        }
        addNotNullConstraint(tableName: "ASYNC_MAIL_HEADER", columnName: "ID", columnDataType: "NUMBER(19)")
    }

    changeSet(author: "Deepak Goyal", id: "asyncMailHeaderPkAdded") {
        preConditions(onFail: 'MARK_RAN') {
            not {
                primaryKeyExists(tableName: "ASYNC_MAIL_HEADER", primaryKeyName: "PK_ASYNC_MAIL_HEADER")
            }
        }
        addPrimaryKey(tableName: "ASYNC_MAIL_HEADER", columnNames: "ID", constraintName: "PK_ASYNC_MAIL_HEADER")
    }

    changeSet(author: "Deepak Goyal", id: "asyncMailHeaderMsgIdxAdded") {
        preConditions(onFail: 'MARK_RAN') {
            not {
                indexExists(indexName: "IDX_ASYNC_MAIL_HEADER_MSG")
            }
        }
        createIndex(tableName: "ASYNC_MAIL_HEADER", indexName: "IDX_ASYNC_MAIL_HEADER_MSG") {
            column(name: "MESSAGE_ID")
        }
    }

}
