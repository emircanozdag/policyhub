      *================================================================*
      * COPYBOOK : VEHREC                                              *
      * PURPOSE  : VEHICLE REGISTRY / KASKO VALUE LIST DAILY EXTRACT   *
      * DATASET  : PROD.POLHUB.VEHREG.DAILY  RECFM=FB  LRECL=100       *
      * LAYOUT   : 1 HEADER (H), N DETAIL (D), 1 TRAILER (T)           *
      * NOTE     : VD-KASKO-VALUE HAS 2 IMPLIED DECIMALS (NO POINT).   *
      *            VT-RECORD-COUNT = NUMBER OF DETAIL RECORDS.         *
      *================================================================*
       01  VEHREC-HEADER.
           05  VH-REC-TYPE             PIC X(01)  VALUE 'H'.
           05  VH-FILE-DATE            PIC 9(08).
      *                                YYYYMMDD BUSINESS DATE
           05  VH-SOURCE-SYSTEM        PIC X(08).
           05  FILLER                  PIC X(83).
      *
       01  VEHREC-DETAIL.
           05  VD-REC-TYPE             PIC X(01)  VALUE 'D'.
           05  VD-PLATE-NO             PIC X(10).
           05  VD-CHASSIS-NO           PIC X(17).
           05  VD-BRAND                PIC X(15).
           05  VD-MODEL                PIC X(20).
           05  VD-MODEL-YEAR           PIC 9(04).
           05  VD-KASKO-VALUE          PIC 9(11)V99.
           05  VD-STATUS               PIC X(01).
               88  VD-ACTIVE                      VALUE 'A'.
               88  VD-SCRAPPED                    VALUE 'S'.
           05  FILLER                  PIC X(19).
      *
       01  VEHREC-TRAILER.
           05  VT-REC-TYPE             PIC X(01)  VALUE 'T'.
           05  VT-RECORD-COUNT         PIC 9(09).
           05  FILLER                  PIC X(90).
