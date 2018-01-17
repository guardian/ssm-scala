package com.gu.ssm

import java.util.concurrent.atomic.AtomicBoolean

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementAsync
import com.googlecode.lanterna.{TerminalPosition, TerminalSize}
import com.googlecode.lanterna.gui2.Interactable.Result
import com.googlecode.lanterna.gui2._
import com.googlecode.lanterna.gui2.dialogs.{MessageDialog, WaitingDialog}
import com.googlecode.lanterna.input.{KeyStroke, KeyType}
import com.googlecode.lanterna.terminal.{DefaultTerminalFactory, Terminal, TerminalResizeListener}
import com.gu.ssm.utils.attempt.{Attempt, FailedAttempt}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}


class InteractiveProgram(client: AWSSimpleSystemsManagementAsync)(implicit ec: ExecutionContext) extends LazyLogging {
  val ui = new InteractiveUI(this)

  def main(setupAttempt: Attempt[(List[Instance], String)])(implicit ec: ExecutionContext): Unit = {
    Future {
      // start UI on a new thread (it blocks while it listens for keyboard input)
      ui.start()
    }

    // update UI when we're ready to get started
    setupAttempt.onComplete { case Right((instances, username)) =>
      ui.ready(instances, username)
    }
  }

  def executeCommand(command: String, instances: List[Instance], username: String): Unit = {
    IO.executeOnInstances(instances, username, command, client).onComplete {
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
  val screen = terminalFactory.createScreen()
  val textGUI = new MultiWindowTextGUI(screen)

  screen.startScreen()

  def loadingWindow(): WaitingDialog = {
    WaitingDialog.createDialog("Loading...", "Loading instance information")
  }

  def mainWindow(instances: List[Instance], username: String, results: List[(Instance, Either[CommandStatus, CommandResult])]): BasicWindow = {
    val window = new BasicWindow(username)

    val initialSize = screen.getTerminal.getTerminalSize
    val contentPanel = new Panel(new LinearLayout()).setPreferredSize(fullscreenPanelSize(initialSize))
    val layoutManager: LinearLayout = contentPanel.getLayoutManager.asInstanceOf[LinearLayout]
    layoutManager.setSpacing(3)

    val resizer = new TerminalResizeListener {
      override def onResized(terminal: Terminal, newSize: TerminalSize): Unit =
        contentPanel.setPreferredSize(fullscreenPanelSize(newSize))
    }

    contentPanel.addComponent(new Label("command to run"))
    val cmdInput = new TextBox(new TerminalSize(40, 1)) {
//      override def handleKeyStroke(keyStroke: KeyStroke): Result = {
//        keyStroke.getKeyType match {
//          case KeyType.Enter =>
//            program.executeCommand(this.getText, instances, username)
//            Result.HANDLED
//          case _ =>
//            super.handleKeyStroke(keyStroke)
//        }
//      }
    }
    contentPanel.addComponent(cmdInput)

    if (results.nonEmpty) {
      contentPanel.addComponent(new EmptySpace())
      contentPanel.addComponent(new Separator(Direction.HORIZONTAL))
      results.foreach {
        case (instance, Right(result)) =>
          contentPanel.addComponent(new Label(instance.id))
          contentPanel.addComponent(new Label(result.stdOut))
          contentPanel.addComponent(new Label(result.stdErr))
        case (instance, Left(commandStatus)) =>
          contentPanel.addComponent(new Label(instance.id))
          contentPanel.addComponent(new Label(commandStatus.toString))
      }
    }

    contentPanel.addComponent(new EmptySpace())
    contentPanel.addComponent(new Separator(Direction.HORIZONTAL))
    contentPanel.addComponent(new Button("Close", new Runnable() {
      override def run(): Unit = {
        window.close()
        program.exit()
      }
    }))

    window.setComponent(contentPanel)
    window
  }

  def fullscreenPanelSize(newSize: TerminalSize): TerminalSize = {
    new TerminalSize(newSize.getColumns - 4, newSize.getRows - 4)
  }

  def start(): Unit = {
    val window = loadingWindow()
    textGUI.addWindowAndWait(window)
  }

  def ready(instances: List[Instance], username: String): Unit = {
    textGUI.removeWindow(textGUI.getActiveWindow)
    textGUI.addWindow(mainWindow(instances, username, Nil))
    textGUI.updateScreen()
  }

  def displayResults(instances: List[Instance], username: String, results: List[(Instance, Either[CommandStatus, CommandResult])]): Unit = {
    textGUI.removeWindow(textGUI.getActiveWindow)
    textGUI.addWindow(mainWindow(instances, username, results))
    textGUI.updateScreen()
  }

  def displayError(fa: FailedAttempt): Unit = {
    MessageDialog.showMessageDialog(textGUI, "Error", fa.failures.map(_.friendlyMessage).mkString(", "))
//    textGUI.removeWindow(textGUI.getActiveWindow)
//    textGUI.addWindow(WaitingDialog.createDialog("Error", fa.failures.map(_.friendlyMessage).mkString(", ")))
  }
}
