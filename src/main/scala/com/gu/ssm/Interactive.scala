package com.gu.ssm

import com.googlecode.lanterna.{TerminalSize, TextColor}
import com.googlecode.lanterna.gui2.Interactable.Result
import com.googlecode.lanterna.gui2._
import com.googlecode.lanterna.gui2.dialogs.{MessageDialog, WaitingDialog}
import com.googlecode.lanterna.input.{KeyStroke, KeyType}
import com.googlecode.lanterna.terminal.{DefaultTerminalFactory, Terminal, TerminalResizeListener}
import com.gu.ssm.Main.SSMConfig
import com.gu.ssm.utils.attempt.FailedAttempt
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}


class InteractiveProgram(ssmConfig: SSMConfig)(implicit ec: ExecutionContext) extends LazyLogging {
  val ui = new InteractiveUI(this)

  // start UI on a new thread (it blocks while it listens for keyboard input)
  Future {
    ui.start()
  }
  // update UI when we're ready to get started
  ui.ready(ssmConfig.targets.map(i => i.id), ssmConfig.name)

  /**
    * Kick off execution of a new command and update UI when it returns
    */
  def executeCommand(command: String, instances: List[InstanceId], username: String): Unit = {
    IO.executeOnInstances(instances, username, command, ssmConfig.ssmClient).onComplete {
      case Right(results) =>
        ui.displayResults(instances, username, results)
      case Left(fa) =>
        ui.displayError(fa)
    }
  }

  def exit(): Unit = {
    System.exit(0)
  }
}

class InteractiveUI(program: InteractiveProgram) extends LazyLogging {
  val terminalFactory = new DefaultTerminalFactory()
  private val screen = terminalFactory.createScreen()
  private val guiThreadFactory = new SeparateTextGUIThread.Factory()
  val textGUI = new MultiWindowTextGUI(guiThreadFactory, screen)
  screen.startScreen()

  /**
    * Create window that displays the main UI along with the results of the previous command
    */
  def mainWindow(instances: List[InstanceId], username: String, results: List[(InstanceId, Either[CommandStatus, CommandResult])]): BasicWindow = {
    val window = new BasicWindow(username)

    val initialSize = screen.getTerminal.getTerminalSize
    val contentPanel = new Panel(new LinearLayout())
      .setPreferredSize(fullscreenPanelSize(initialSize))
    val layoutManager = contentPanel.getLayoutManager.asInstanceOf[LinearLayout]
    layoutManager.setSpacing(0)

    val resizer = new TerminalResizeListener {
      override def onResized(terminal: Terminal, newSize: TerminalSize): Unit =
        contentPanel.setPreferredSize(fullscreenPanelSize(newSize))
    }

    contentPanel.addComponent(new Label("command to run"))
    val cmdInput = new TextBox(new TerminalSize(40, 1)) {
      override def handleKeyStroke(keyStroke: KeyStroke): Result = {
        keyStroke.getKeyType match {
          case KeyType.Enter =>
            program.executeCommand(this.getText, instances, username)
            val loading = WaitingDialog.createDialog("Executing...", "Executing command on instances")
            textGUI.addWindow(loading)
            Result.HANDLED
          case _ =>
            super.handleKeyStroke(keyStroke)
        }
      }
    }
    contentPanel.addComponent(cmdInput)

    // show results, if present
    if (results.nonEmpty) {
      val outputs = results.zipWithIndex.map { case ((_, result), i) =>
        val outputStreams = result match {
          case Right(cmdResult) =>
            cmdResult
          case Left(status) =>
            CommandResult("", status.toString)
        }
        i -> outputStreams
      }.toMap

      val errOutputBox = new Label(outputs(0).stdErr)
      errOutputBox.setForegroundColor(TextColor.ANSI.RED)
      val stdOutputBox = new Label(outputs(0).stdOut)

      val instancesComboBox = new ComboBox(instances.map(_.id):_*).addListener { (selectedIndex: Int, _: Int) =>
        errOutputBox.setText(outputs(selectedIndex).stdErr)
        stdOutputBox.setText(outputs(selectedIndex).stdOut)
      }
      contentPanel.addComponent(instancesComboBox)

      contentPanel.addComponent(new EmptySpace())
      contentPanel.addComponent(new Separator(Direction.HORIZONTAL))
      contentPanel.addComponent(errOutputBox)
      contentPanel.addComponent(stdOutputBox)
    }

    // close button
    contentPanel.addComponent(new EmptySpace())
    contentPanel.addComponent(new Separator(Direction.HORIZONTAL))
    contentPanel.addComponent(new Button("Close", () => {
      window.close()
      program.exit()
    }))

    window.setComponent(contentPanel)
    window
  }

  /**
    * "fullscreen" with space for panel borders
    */
  def fullscreenPanelSize(newSize: TerminalSize): TerminalSize = {
    new TerminalSize(newSize.getColumns - 4, newSize.getRows - 4)
  }

  def start(): Unit = {
    logger.debug("Starting interactive UI")
    textGUI.getGUIThread.asInstanceOf[AsynchronousTextGUIThread].start()
    val window = WaitingDialog.createDialog("Loading...", "Loading instance information")
    textGUI.addWindowAndWait(window)
  }

  def ready(instances: List[InstanceId], username: String): Unit = {
    logger.trace("resolved instances and username, UI ready")
    textGUI.removeWindow(textGUI.getActiveWindow)
    textGUI.addWindow(mainWindow(instances, username, Nil))
    textGUI.updateScreen()
  }

  def displayResults(instances: List[InstanceId], username: String, results: List[(InstanceId, Either[CommandStatus, CommandResult])]): Unit = {
    logger.trace("displaying results")
    textGUI.removeWindow(textGUI.getActiveWindow)
    textGUI.addWindow(mainWindow(instances, username, results))
    textGUI.updateScreen()
  }

  def displayError(fa: FailedAttempt): Unit = {
    logger.trace("displaying error")
    textGUI.removeWindow(textGUI.getActiveWindow)
    MessageDialog.showMessageDialog(textGUI, "Error", fa.failures.map(_.friendlyMessage).mkString(", "))
  }
}
