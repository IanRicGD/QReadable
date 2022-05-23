package com.tallercmovil.qreadable

import android.Manifest
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.ContactsContract
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.zxing.Result
import me.dm7.barcodescanner.zxing.ZXingScannerView
import java.net.MalformedURLException
import java.net.URL
import java.util.regex.Pattern


class QR : AppCompatActivity(), ZXingScannerView.ResultHandler {

    private val PERMISO_CAMARA = 1
    private var scannerView: ZXingScannerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        scannerView = ZXingScannerView(this)
        setContentView(scannerView)

        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            if(checarPermiso()){
                //se concedió el permiso
            }else{
                solicitarPermiso()
            }
        }

        scannerView?.setResultHandler(this)
        scannerView?.startCamera()
    }

    private fun solicitarPermiso() {
        ActivityCompat.requestPermissions(this@QR, arrayOf(Manifest.permission.CAMERA),PERMISO_CAMARA)
    }

    private fun checarPermiso(): Boolean {
        return (ContextCompat.checkSelfPermission(this@QR, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }

    @SuppressLint("IntentReset")
    override fun handleResult(p0: Result?) {
        //código QR leído
        val scanResult = p0?.text

        val list = scanResult?.split(":",";")


        try{
            if (list?.get(0).toString() == "SMSTO"){
                val list = scanResult?.split(":",limit=3)

                Log.d("QR_LEIDO", "es mensaje")

                val uri = Uri.parse("smsto:${list?.get(1).toString()}")
                val intent = Intent(Intent.ACTION_SENDTO, uri)
                intent.putExtra("sms_body", list?.get(2).toString())
                startActivity(intent)
                finish()
            }
            if (list?.get(0).toString() == "MATMSG"){

                Log.d("QR_LEIDO", "es correo")

                val intent = Intent(Intent.ACTION_SENDTO)
                val emails = arrayOf(list?.get(2).toString())
                intent.type = "*/*"
                intent.data = Uri.parse("mailto:")
                intent.putExtra(Intent.EXTRA_EMAIL,emails)
                intent.putExtra(Intent.EXTRA_SUBJECT, list?.get(4).toString())
                intent.putExtra(Intent.EXTRA_TEXT, list?.get(6).toString())

                if(intent.resolveActivity(packageManager) != null) {//revisa si hay un app que pueda manejar el intent
                    startActivity(intent)
                    finish()
                }
                else{
                    Toast.makeText(this@QR, "No hay una app para mandar el correo", Toast.LENGTH_LONG).show()
                    finish()
                }

            }
            if(list?.get(0).toString() == "BEGIN"){
                Log.d("QR_LEIDO", "es VCARD")
                Log.d("QR_LEIDO", list.toString())

                val email = getEmailAddressesInString(list.toString())
                val emailVcard = email?.get(0).toString()

                val numbers=getCellNumbersInString(list.toString())
                val cellNumber= numbers?.get(0).toString().drop(6)

                val names=getNameInString(list.toString())
                val namesCard= names.toString().replace("[","").replace("]","").split(",")

                Log.d("QR_LEIDO", namesCard[1])

                val intent = Intent(Intent.ACTION_INSERT)
                intent.type = ContactsContract.Contacts.CONTENT_TYPE

                intent.putExtra(ContactsContract.Intents.Insert.NAME, namesCard[2]+" "+namesCard[1])
                intent.putExtra(ContactsContract.Intents.Insert.PHONE, cellNumber)
                intent.putExtra(ContactsContract.Intents.Insert.EMAIL, emailVcard)

                this.startActivity(intent)
            }
            else{
                Log.d("QR_LEIDO", scanResult.toString())
                val url = URL(scanResult)
                val i = Intent(Intent.ACTION_VIEW)
                i.data = Uri.parse(scanResult)
                startActivity(i)
                finish()
            }
        }catch(e: MalformedURLException){
            AlertDialog.Builder(this@QR)
                .setTitle("Error")
                .setMessage("El código QR no es válido para la aplicación")
                .setPositiveButton("Aceptar", DialogInterface.OnClickListener { dialogInterface, i ->
                    dialogInterface.dismiss()
                    finish()
                })
                .create()
                .show()
        }


    }

    override fun onResume() {
        super.onResume()
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if(checarPermiso()){
                if(scannerView == null){
                    scannerView = ZXingScannerView(this)
                    setContentView(scannerView)
                }

                scannerView?.setResultHandler(this)
                scannerView?.startCamera()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scannerView?.stopCamera()
    }

    fun getEmailAddressesInString(text: String): ArrayList<String>? {
        val emails: ArrayList<String> = ArrayList()
        val matcher =
            Pattern.compile("[A-Z0-9a-z._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,6}")
                .matcher(text)
        while (matcher.find()) {
            emails.add(matcher.group())
        }
        return emails
    }

    fun getCellNumbersInString(text: String): ArrayList<String>? {
        val numbers: ArrayList<String> = ArrayList()
        val matcher =
            Pattern.compile("CELL, [0-9]+")
                .matcher(text)
        while (matcher.find()) {
            numbers.add(matcher.group())
        }
        return numbers
    }

    fun getNameInString(text: String): ArrayList<String>? {
        val names: ArrayList<String> = ArrayList()
        val matcher =
            Pattern.compile("N, [a-zA-Z]+, [a-zA-Z]+")
                .matcher(text)
        while (matcher.find()) {
            names.add(matcher.group())
        }
        return names
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        when(requestCode){

            PERMISO_CAMARA -> {
                if(grantResults.isNotEmpty()){
                    if(grantResults[0]!=PackageManager.PERMISSION_GRANTED){
                        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            if(shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)){
                                AlertDialog.Builder(this@QR)
                                    .setTitle("Permiso requerido")
                                    .setMessage("Se necesita acceder a la cámara para leer los códigos QR")
                                    .setPositiveButton("Aceptar", DialogInterface.OnClickListener { dialogInterface, i ->
                                        requestPermissions(arrayOf(Manifest.permission.CAMERA), PERMISO_CAMARA)
                                    })
                                    .setNegativeButton("Cancelar", DialogInterface.OnClickListener { dialogInterface, i ->
                                        dialogInterface.dismiss()
                                        finish()
                                    })
                                    .create()
                                    .show()
                            }else{
                                Toast.makeText(this@QR, "El permiso de la cámara no se ha concedido", Toast.LENGTH_LONG).show()
                                finish()
                            }
                        }
                    }
                }
            }

        }
    }

}