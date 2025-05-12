package com.gu.ssm

import com.amazonaws.regions.Region
import com.googlecode.lanterna.{TerminalSize, TextColor}
import com.googlecode.lanterna.gui2.Interactable.Result
import com.googlecode.lanterna.gui2._
import com.googlecode.lanterna.gui2.dialogs.{MessageDialog, WaitingDialog}
import com.googlecode.lanterna.input.{KeyStroke, KeyType}
import com.googlecode.lanterna.terminal.{DefaultTerminalFactory, Terminal, TerminalResizeListener}
import com.gu.ssm.utils.attempt.{Attempt, ErrorCode, FailedAttempt, Failure}
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}

class InteractiveProgram(val awsClients: AWSClients)(implicit ec: ExecutionContext) extends LazyLogging {
  val ui = new InteractiveUI(this)

  def main(profile: Option[String], region: Region, executionTarget: ExecutionTarget): Unit = {
    // start UI on a new thread (it blocks while it listens for keyboard input)
    Future {
      ui.start()
    }
    val configAttempt = for {
      config <- IO.getSSMConfig(awsClients.ec2Client, awsClients.stsClient, executionTarget)
      _ <- Attempt.fromEither(Logic.checkInstancesList(config))
    } yield config

    configAttempt.onComplete {
      case Right(SSMConfig(targets, name)) => {
        val incorrectInstancesFromInstancesTag = Logic.computeIncorrectInstances(executionTarget, targets.map(i => i.id))
        ui.ready(targets.map(i => i.id), name, incorrectInstancesFromInstancesTag)
      }
      case Left(failedAttempt) =>
        ui.displayError(failedAttempt)
        ui.ready(List(), "", Nil)
    }
  }

  /**
    * Kick off execution of a new command and update UI when it returns
    */
  def executeCommand(command: String, instances: List[InstanceId], username: String, instancesNotFound: List[InstanceId]): Unit = {
    IO.executeOnInstances(instances, username, command, awsClients.ssmClient).onComplete {
      case Right(results) =>
        ui.displayResults(instances, username, ResultsWithInstancesNotFound(results, instancesNotFound))
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
  def mainWindow(instances: List[InstanceId], username: String, extendedResults: ResultsWithInstancesNotFound): BasicWindow = {

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

    if (instances.nonEmpty) {
      contentPanel.addComponent(new Label("Command to run"))
      val cmdInput = new TextBox(new TerminalSize(40, 1)) {
        override def handleKeyStroke(keyStroke: KeyStroke): Result = {
          keyStroke.getKeyType match {
            case KeyType.Enter =>
              program.executeCommand(this.getText, instances, username, extendedResults.instancesNotFound)
              val loading = WaitingDialog.createDialog("Executing...", "Executing command on instances")
              textGUI.addWindow(loading)
              Result.HANDLED
            case _ =>
              super.handleKeyStroke(keyStroke)
          }
        }
      }
      contentPanel.addComponent(cmdInput)
    }

    if (extendedResults.instancesNotFound.nonEmpty) {
      contentPanel.addComponent(new EmptySpace())
      contentPanel.addComponent(new Label(s"The following instance(s) could not be found: ${extendedResults.instancesNotFound.map(_.id).mkString(", ")}").setForegroundColor(TextColor.ANSI.RED))
      contentPanel.addComponent(new EmptySpace())
    }

    // show results, if present
    if (extendedResults.results.nonEmpty) {
      val outputs = extendedResults.results.zipWithIndex.map { case ((_, result), i) =>
        val outputStreams = result match {
          case Right(cmdResult) =>
            cmdResult
          case Left(status) =>
            CommandResult("", status.toString, commandFailed = true)
        }
        i -> outputStreams
      }.toMap

      val errOutputBox = new Label(outputs(0).stdErr)
      errOutputBox.setForegroundColor(TextColor.ANSI.RED)
      val stdOutputBox = new Label(outputs(0).stdOut)

      val listener = new ComboBox.Listener {
        override def onSelectionChanged(selectedIndex: Int, previousSelection: Int, changedByUserInteraction: Boolean): Unit = {
          errOutputBox.setText(outputs(selectedIndex).stdErr)
          stdOutputBox.setText(outputs(selectedIndex).stdOut)
        }
      }

      val instancesComboBox: ComboBox[String] = new ComboBox(instances.map(_.id)*).addListener(listener)
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

  def ready(instances: List[InstanceId], username: String, instancesToReport: List[InstanceId]): Unit = {
    logger.trace("resolved instances and username, UI ready")
    textGUI.removeWindow(textGUI.getActiveWindow)
    textGUI.addWindow(mainWindow(instances, username, ResultsWithInstancesNotFound(Nil, instancesToReport)))
    textGUI.updateScreen()
  }

  def searching(): Unit = {
    logger.trace("waiting to resolve instances and username, UI ready")
    textGUI.removeWindow(textGUI.getActiveWindow)
    textGUI.addWindow(mainWindow(List(), "", ResultsWithInstancesNotFound(Nil, Nil)))
    textGUI.updateScreen()
  }

  def displayResults(instances: List[InstanceId], username: String, extendedResults: ResultsWithInstancesNotFound): Unit = {
    logger.trace("displaying results")
    textGUI.removeWindow(textGUI.getActiveWindow)
    textGUI.addWindow(mainWindow(instances, username, extendedResults))
    textGUI.updateScreen()
  }

  def displayError(fa: FailedAttempt): Unit = {
    logger.trace("displaying error")
    textGUI.removeWindow(textGUI.getActiveWindow)
    MessageDialog.showMessageDialog(textGUI, "Error", fa.failures.map(_.friendlyMessage).mkString(", "))
  }
}
