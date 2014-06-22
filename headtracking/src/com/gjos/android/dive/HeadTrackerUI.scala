package com.gjos.android.dive

import android.os.Bundle
import android.widget.{TextView, EditText, RadioGroup, Button}
import com.gjos.android.dive.calc.Vec
import com.gjos.android.dive.connectivity._
import com.gjos.android.dive.sensing.SensorBroker
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Try, Failure, Success}
import scala.concurrent.duration._

class HeadTrackerUI extends RichActivity {
  lazy val sensorBroker = new SensorBroker(this)

  override def onCreate(savedState: Bundle) {
    super.onCreate(savedState)
    this.setContentView(R.layout.main)

    connectButton.setOnClickListener(connectBtnClick)
    sensorBroker.gyroscope.observable.buffer(10).subscribe(onOrientationChanged _)
  }

  private def connectButton: Button = find(R.id.connectButton)
  private def radioGroup: RadioGroup = find(R.id.connectionTypeGroup)
  private def statusText: TextView = find(R.id.statusText)

  private def currentIp: String = find[EditText](R.id.ipEdit).getText.toString

  // Always works since EditText type is set to number
  private def currentPort: Int = find[EditText](R.id.portEdit).getText.toString.toInt

  var currentConnection: Option[Connection] = None

  def connectBtnClick() = currentConnection match {
    case Some(connection) if connection.isOpen => startDisconnect()
    case _ => startConnect()
  }

  protected def startConnect() {
    val connection = createConnection()
    currentConnection = Some(connection)
    uponConnecting()
    connection.open() onComplete uponConnect
  }

  protected def startDisconnect() {
    require(currentConnection.nonEmpty && currentConnection.get.isOpen, "Cannot disconnect, no connection is open.")
    uponDisconnecting()
    currentConnection.get.close() onComplete uponDisconnect
    currentConnection = None
  }

  private def uponConnecting() {
    connectButton.setEnabled(false)
    toast("Connecting...")
  }

  private def uponDisconnecting() {
    connectButton.setEnabled(false)
    toast("Disconnecting...")
  }

  private def createConnection() = radioGroup.getCheckedRadioButtonId match {
    case R.id.tcp => new TcpConnection(currentIp, currentPort)
    case R.id.udp => new UdpConnection(currentIp, currentPort)
    case R.id.bluetooth => new BluetoothConnection
  }

  private def uponConnect(result: Try[Unit]) = inUiThread {
    result match {
      case Success(_) =>
        toast("Connected!")
        connectButton.setText("Disconnect")
        sensorBroker.startListening()
      case Failure(ex) =>
        toast("Failed to connect: " + ex.getMessage)
    }
    connectButton.setEnabled(true)
  }

  private def uponDisconnect(result: Try[Unit]) = inUiThread {
    result match {
      case Success(_) =>
        sensorBroker.stopListening()
        toast("Disconnected.")
        connectButton.setText("Connect")
      case Failure(ex) =>
        toast("Failed to disconnect: " + ex.getMessage)
    }
    connectButton.setEnabled(true)
  }

  var lastMeasurement = currentMicros
  def onOrientationChanged(buffer: Seq[Array[Float]]): Unit = {
    val muSec = currentMicros
    val dt = muSec - lastMeasurement
    lastMeasurement = muSec

    val radiansMoved = Vec(0, 0, 0)
    for (values <- buffer) {
      radiansMoved += Vec(values(0), values(1), values(2))
    }
    radiansMoved *= dt / 1000000f
    radiansMoved /= buffer.size

    val csv = radiansMoved.toCsv()
    statusText.setText(s"Radians moved: $csv")
    currentConnection foreach (_.send(csv) onFailure handleFailure)
  }

  def currentMicros = System.nanoTime / 1000

  def handleFailure: PartialFunction[Throwable, Unit] = {
    case ex: RuntimeException =>
      startDisconnect()
      statusText.setText(s"Failed sending: ${ex.getMessage}")
  }
}
