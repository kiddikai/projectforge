/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2023 Micromata GmbH, Germany (www.micromata.com)
//
// ProjectForge is dual-licensed.
//
// This community edition is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License as published
// by the Free Software Foundation; version 3 of the License.
//
// This community edition is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
// Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, see http://www.gnu.org/licenses/.
//
/////////////////////////////////////////////////////////////////////////////

package org.projectforge.rest.sipgate

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test


class SipgateDirectCallServiceTest {
  @Test
  fun numbersTest() {

  }
  @Test
  fun personalPhoneIdentifiersTest() {
    Assertions.assertNull(SipgateDirectCallService.getNormalizedPersonalPhoneIdentifiers(null))
    Assertions.assertNull(SipgateDirectCallService.getNormalizedPersonalPhoneIdentifiers(""))
    Assertions.assertEquals("21", SipgateDirectCallService.getNormalizedPersonalPhoneIdentifiers("21"))
    Assertions.assertEquals("21,827", SipgateDirectCallService.getNormalizedPersonalPhoneIdentifiers("21, 827"))
    Assertions.assertEquals("21,827", SipgateDirectCallService.getNormalizedPersonalPhoneIdentifiers("21, 827,21"))
  }
}
