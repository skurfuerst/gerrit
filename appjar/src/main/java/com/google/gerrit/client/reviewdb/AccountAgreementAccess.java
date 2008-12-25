// Copyright 2008 Google Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.client.reviewdb;

import com.google.gwtorm.client.Access;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.PrimaryKey;
import com.google.gwtorm.client.Query;
import com.google.gwtorm.client.ResultSet;

public interface AccountAgreementAccess extends
    Access<AccountAgreement, AccountAgreement.Key> {
  @PrimaryKey("key")
  AccountAgreement get(AccountAgreement.Key key) throws OrmException;

  @Query("WHERE key.accountId = ? ORDER BY acceptedOn DESC")
  ResultSet<AccountAgreement> byAccount(Account.Id id) throws OrmException;

  @Query("WHERE status = '" + AccountAgreement.NEW_CODE
      + "' ORDER BY acceptedOn DESC")
  ResultSet<AccountAgreement> unreviewed() throws OrmException;
}