/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2019 Micromata GmbH, Germany (www.micromata.com)
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

package org.projectforge.setup

import com.googlecode.lanterna.TerminalSize
import com.googlecode.lanterna.gui2.BasicWindow
import com.googlecode.lanterna.gui2.MultiWindowTextGUI
import com.googlecode.lanterna.screen.Screen
import java.io.File

class GUIContext(
        val setupMain: SetupMain,
        val textGUI: MultiWindowTextGUI,
        val screen: Screen,
        val terminalSize: TerminalSize
) {
    class SetupData {
        var applicationHomeDir: File? = null
    }
    var currentWindow: BasicWindow? = null
    var chooseDirectoryWindow: ChooseDirectoryWindow? = null
    var initializeWindow: InitializeWindow? = null
    val windowSize: TerminalSize
    val setupData = SetupData()

    init {
        windowSize = TerminalSize(terminalSize.columns - 15, terminalSize.rows - 5)
    }
}
