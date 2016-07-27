package xyz.semio.spherodemo;

import android.bluetooth.BluetoothAdapter;
import android.os.Handler;
import android.os.Bundle;

import xyz.semio.Function;
import xyz.semio.GraphInfo;
import xyz.semio.Interaction;
import xyz.semio.InteractionPlayer;
import xyz.semio.Semio;
import xyz.semio.Session;
import xyz.semio.SpeechHelper;
import xyz.semio.SpeechPlayStrategy;

import com.orbotix.ConvenienceRobot;
import com.orbotix.DualStackDiscoveryAgent;
import com.orbotix.Sphero;
import com.orbotix.common.DiscoveryException;
import com.orbotix.common.Robot;
import com.orbotix.common.RobotChangedStateListener;

import android.app.Activity;

import android.content.Intent;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.EditText;
import android.widget.Toast;

import java.util.List;

public class MainActivity extends Activity {
  private static final String TAG = "semio";

  private EditText _username;
  private EditText _password;
  private Button _submit;
  private ListView _graphs;
  private List<GraphInfo> _graphInfos;
  private Button _selectDevice;

  private Session _session;

  private SpeechHelper _speech = new SpeechHelper(this);

  private ConvenienceRobot _robot = null;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    setContentView(R.layout.activity_main);

    this._username = (EditText) findViewById(R.id.username);
    this._password = (EditText) findViewById(R.id.password);
    this._submit = (Button) findViewById(R.id.submit);
    this._graphs = (ListView) findViewById(R.id.graphs);

    this._graphs.setOnItemClickListener(new ListView.OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        String graphId = _graphInfos.get(position).getId();
        startInteraction(graphId);
      }
    });

    DualStackDiscoveryAgent.getInstance().addRobotStateListener(new RobotChangedStateListener() {
      @Override
      public void handleRobotChangedState(final Robot robot, RobotChangedStateNotificationType type) {
        System.out.println("CONNECTED: " + robot.getName());
        new Handler(MainActivity.this.getMainLooper()).post(new Runnable() {
          @Override
          public void run() {
            Toast t = Toast.makeText(MainActivity.this, "Connceted to " + robot.getName() + "!", Toast.LENGTH_SHORT);
            t.show();
          }
        });
        _robot = new Sphero(robot);
      }
    });

    this._submit.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        // Try to login
        Semio.createSession(_username.getText().toString(), _password.getText().toString()).then(new Function<Session, Object>() {
          @Override
          public Object apply(Session session) {
            if(session == null)
            {
              new Handler(MainActivity.this.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                  Toast t = Toast.makeText(MainActivity.this, "Failed to login!", Toast.LENGTH_SHORT);
                  t.show();
                }
              });

              return null;
            }

            _session = session;
            // If we successfully logged in, fetch the chatbots associated with this account
            populateGraphs();
            return null;
          }
        });
      }
    });


    _speech.init();
  }

  // Fetch the chatbots associated with a Semio account
  private void populateGraphs() {
    if(this._session == null) return;
    this._session.getGraphs().then(new Function<List<GraphInfo>, Object>() {
      @Override
      public Object apply(List<GraphInfo> graphs) {
        if(graphs == null) return null;
        _graphInfos = graphs;
        final String[] items = new String[graphs.size()];
        for(int i = 0; i < graphs.size(); ++i) items[i] = graphs.get(i).getName();
        Handler mainHandler = new Handler(MainActivity.this.getMainLooper());
        mainHandler.post(new Runnable() {
          @Override
          public void run() {
            _graphs.setAdapter(new ArrayAdapter<String>(MainActivity.this, android.R.layout.simple_list_item_1, items));
          }
        });
        return null;
      }
    });
  }

  // Play an interaction
  private void startInteraction(final String id) {
    _session.createInteraction(id).then(new Function<Interaction, Object>() {
      @Override
      public Object apply(Interaction interaction) {
        if(interaction == null) {
          new Handler(MainActivity.this.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
              Toast t = Toast.makeText(MainActivity.this, "Failed to fetch interaction!", Toast.LENGTH_SHORT);
              t.show();
            }
          });

          return null;
        }
        InteractionPlayer player = new InteractionPlayer(interaction, new SpeechPlayStrategy(_speech));
        player.getScriptInterpreter().addBinding(_robot, "sphero");
        player.start();
        return null;
      }
    });
  }
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    getMenuInflater().inflate(R.menu.activity_main, menu);
    return true;
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    this._speech.processResult(requestCode, resultCode, data);
  }

  @Override
  protected void onStart() {
    super.onStart();
    try {
      DualStackDiscoveryAgent.getInstance().startDiscovery(this);
    } catch(DiscoveryException e) {
      e.printStackTrace();
    }
  }

  @Override
  protected void onStop() {
    if(this._robot != null) this._robot.disconnect();
    super.onStop();
  }

  @Override
  public void onDestroy() {
    super.onDestroy();
  }
}