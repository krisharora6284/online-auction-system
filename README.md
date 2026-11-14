# Rohit Sharma Cricket Auction Pro

A Java-only backend with a responsive browser UI for a cricket player auction classroom demonstration. The entire UI is embedded in `Main.java`, so there are no missing static-file paths, frontend build steps, or third-party Java dependencies.

## Requirements
- JDK 17 or newer (tested with Java 25)
- Browser
- Port 8080 available

## Run on Windows PowerShell
Open this folder in VS Code, then run these commands from the folder containing `README.md`:

```powershell
javac -d out src\main\java\com\auction\Main.java
java -cp out com.auction.Main
```

Open http://localhost:8080 in Chrome. Keep the terminal open while using the app.

If port 8080 is already occupied, stop the other app with Ctrl+C in its terminal, or run a different port:
```powershell
$env:PORT="8081"
java -cp out com.auction.Main
```
Then open http://localhost:8081.

## Features
- Rohit Sharma is the first player on the auction floor
- 12 sample cricket players, 4 fictional franchises, ₹100 crore demo purse per team
- Place bid, sell to highest bidder, mark unsold, reset demo
- Bid validation (minimum increment ₹25 lakh, base price, team remaining purse)
- Live team purse, event feed, catalogue search, role filter, auction summary
- Data is in memory and resets when the server restarts; no database required

## API
- `GET /api/health`
- `GET /api/state`
- `POST /api/select` with `playerId`
- `POST /api/bid` with `teamId` and `amount`
- `POST /api/sell`
- `POST /api/unsold`
- `POST /api/reset`

## Important
This is a simulated educational auction. Team names, starting purses, and base prices are demo data; the project does not handle real money, real contracts, or live bidding from multiple remote users.
