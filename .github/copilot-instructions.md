# Project Overview
This project is a a personal financial management application that helps users track their income, expenses, and savings. It provides features such as budgeting, expense categorization, financial goal setting, and reporting.

# Architecture
This application follows the Model-View-Controller (MVC) architecture. The backend is built using Java, and the frontend is designed to have multiple instantiations, command line, text messaging, web app, iphone app, android app. I accomplishes the by by defining the view layer as an interface and having multiple implementations of the view, such as cmdLine for command line and excel for Microsoft Excel (where the user interacts with the application by editing and viewing Excel spreadsheets).  It is important that the controller layer never directly interacts with the user.  For user interaction, controllers must call methods in the view interface and the current implementation of the view interface will do the actual interaction.  The application uses MySQL for data storage.

# Source Control
This project uses Git for version control. I will test the code that you write.  Wait for me to tell you to commit changes, which I will do after I have tested it and approved it. When I do, provide a concise commit message summarizing the changes made.