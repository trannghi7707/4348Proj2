/*
 	CS 4348.002
 */

import java.io.FileNotFoundException;
import java.util.concurrent.Semaphore;

public class Project2 {
	
	// The number of patients and doctors
	public static int numPatients, numDoctors;
	
	// The semaphores used to represent patients, nurses, and doctors
	static Semaphore CounterAvailable = new Semaphore(1, true);
	static Semaphore customerEnterCounter = new Semaphore(0, true);
	static Semaphore goToWaitingRoom = new Semaphore(0, true);
	static Semaphore waitingInWaitingRoom = new Semaphore(0, true);
	static Semaphore nurses[], doctors[], patientDoctorInteraction[];
	
	static int currentPatientToRegister = 0; // Keeps track of the current patient the receptionist is serving
	static int[] currentPatientOfDoctor; // Keeps track of the current patient the doctor is with
	
	public static void main(String[] args) throws FileNotFoundException
	{
		// Verifies the validity of the command-line inputs
		if (args.length >= 2)
		{
			// Checks to see if the number of patients is an integer
			try
			{ numPatients = Integer.parseInt(args[0]); } 
			catch (Exception e)
			{
				System.err.println("ERROR: "+args[0]+" is not an integer. The number of customers must be an integer.");
				System.exit(1);
			}
			
			// Checks to see if the number of doctors/nurses is an integer
			try
			{ numDoctors = Integer.parseInt(args[1]); }
			catch (Exception e)
			{
				System.err.println("ERROR: "+args[1]+" is not an integer. The number of doctors must be an integer.");
				System.exit(1);
			}
		} else
		{
			// Not enough arguments passed through the command line
			System.err.println("ERROR: Only "+args.length+" argument(s) provided. Require at least 2.");
			System.exit(1);
		}
		
		/* For testing the program in an IDE */
		//numPatients = 3; numDoctors = 3;
		
		// Clamps the number of patients between 1 and 30
		if (numPatients < 1) 
			numPatients = 1;
		else if (numPatients > 30) 
			numPatients = 30;
		
		// Clamps the number of doctors between 1 and 3
		if (numDoctors < 1)
			numDoctors = 1;
		else if (numDoctors > 3)
			numDoctors = 3;
		
		// Instantiates the mailbox of nurses/doctors to semaphores of value 0
		nurses = new Semaphore[numDoctors];
		doctors = new Semaphore[numDoctors];
		for(int i = 0; i < numDoctors; i++)
		{
			nurses[i] = new Semaphore(0, true);
			doctors[i] = new Semaphore(0, true);
		}
		
		// Instantiates the mailbox of paitent-doctor interactions to semaphores of value 0
		patientDoctorInteraction = new Semaphore[numPatients];
		for (int i = 0; i < numPatients; i++)
			patientDoctorInteraction[i] = new Semaphore(0, true);
		
		// Assigns -1 to the current patient of all the doctors (meaning no current patients)
		currentPatientOfDoctor = new int[numDoctors];
		for(int i = 0; i < numDoctors; i++)
			currentPatientOfDoctor[i] = -1;
		
		// Prints the number of patients, nurses, and doctors
		System.out.printf("Run with %d patients, %d nurses, %d doctors\n\n", numPatients, numDoctors, numDoctors);
		
		// Create the receptionist and start his/her process
		Thread receptionist = new Thread(new Receptionist(0));
		receptionist.setDaemon(true); // kills the thread if it is no longer active
		receptionist.start();
		
		// Create nurses and doctors and start their processes
		for (int i = 0; i < numDoctors; i++)
		{
			Thread nurse = new Thread(new Nurse(i));
			Thread doctor = new Thread(new Doctor(i));
			// Kill the threads if they are no longer active
			nurse.setDaemon(true);
			doctor.setDaemon(true);
			// Start the corresponding nurse and doctor threads
			nurse.start();
			doctor.start();
		}
		
		// Create the patients and start their processes
		for (int i = 0; i < numPatients; i++)
			new Thread(new Patient(i)).start();
	}
	
	/*
 		Abstract Person class that assigns a person with a specified ID
 		Any class that extends this class will also be assigned an ID and have access to getId() and print(str) methods
	*/
	public static abstract class Person
	{
		// The ID of the person
		private int id;
		
		// Constructor that creates a Person object with a specified ID
		public Person(int id)
		{
			this.id = id;
		}
		
		// Retrieves the person's ID
		public int getId()
		{
			return id;
		}
		
		// Prints the passed in string and makes the thread sleep for 1 second afterwards
		public void print(String str) throws InterruptedException
		{
			System.out.println(str);
			Thread.sleep(1000);
		}
	}
	
	/*
		Simulates a patient that interacts with the receptionist, a nurse, and a doctor
	*/
	public static class Patient extends Person implements Runnable
	{
		// Constructor that creates a Patient object with a specified ID
		public Patient(int id)
		{
			super(id);
		}
		
		@Override
		public void run()
		{
			try
			{
				// Patient enters, waits for receptionist
				CounterAvailable.acquire();
				print("Patient "+super.id+" enters the waiting room, waits for receptionist");
				currentPatientToRegister = super.id;
				customerEnterCounter.release();
				
				// Patient is done with the receptionist, goes to waiting room
				goToWaitingRoom.acquire();
				print("Patient "+super.id+" leaves receptionist and sits in waiting room");
				CounterAvailable.release();
				
				// Patient waits in waiting room until a nurse calls him or her in
				waitingInWaitingRoom.acquire();
				int doctorId = (int) (Math.random() * numDoctors);
				while (currentPatientOfDoctor[doctorId] != -1)
					doctorId = (int) (Math.random() * numDoctors);
				currentPatientOfDoctor[doctorId] = super.id;
				nurses[doctorId].release();
				
				// Patient enters doctor's office
				patientDoctorInteraction[super.id].acquire();
				print("Patient "+super.id+" enters doctor "+doctorId+"'s office");
				doctors[doctorId].release();
				
				// Patient receives advice from doctor
				patientDoctorInteraction[super.id].acquire();
				print("Patient "+super.id+" receives advice from doctor "+doctorId);
				nurses[doctorId].release();
				
				// Patient leaves
				print("Patient "+super.id+" leaves");
			}
			catch (InterruptedException e) { }
		}
	}
	
	/*
		Simulates a receptionist that serves patients and talks to them one-by-one
	*/
	public static class Receptionist extends Person implements Runnable
	{
		// Constructor that creates a Patient object with a specified ID; ID is not necessary in this situation
		public Receptionist(int id)
		{
			super(id);
		}
		
		/*
		 	Method that instructs nurses to take their assigned patients to their corresponding doctors
		 	for as many patients that he/she can take
		 */
		@Override
		public void run()
		{
			try
			{
				while (true)
				{
					// Wait for patient to come up to the desk
					customerEnterCounter.acquire();
					print("Reckeptionist registers patient "+currentPatientToRegister);
					goToWaitingRoom.release(); // Unblock the next patient
				}
			} catch (InterruptedException e) {}
		}
	}
	
	/*
		Simulates a nurse whose job is to take an assigned patient to his/her corresponding doctor's office
	*/
	public static class Nurse extends Person implements Runnable
	{
		// Constructor that creates a Nurse object with a specified ID
		public Nurse(int id)
		{
			super(id);
		}
		
		/*
		 	Method that instructs nurses to take their assigned patients to their corresponding doctors
		 	for as many patients that he/she can take
		 */
		@Override
		public void run()
		{
			try
			{
				while (true)
				{
					waitingInWaitingRoom.release(); // Unblock the next available patient in the waiting room
					nurses[super.id].acquire(); // Wait until a patient is available and signals to it
					print("Nurse "+super.id+" takes patient "+currentPatientOfDoctor[super.id]+" to doctor's office");
					patientDoctorInteraction[currentPatientOfDoctor[super.id]].release(); // Begin the patient and doctor interaction
					nurses[super.id].acquire(); // Wait until the doctor's appointment with the patient has finished
					currentPatientOfDoctor[super.id] = -1; // Corresponding doctor no longer has an active patient, ready for a new one
				}
			} catch (InterruptedException e) { }
		}
	}
	
	/*
		Simulates a doctor who receives a patient and listens to his/her symptoms
	*/
	public static class Doctor extends Person implements Runnable
	{
		// Constructor that creates a Doctor object with a specified ID
		public Doctor(int id)
		{
			super(id);
		}
		
		/*
		 	Method that handles the interaction between a doctor and his/her patient
		 	for as many patients that he/she can take
		 */
		@Override
		public void run()
		{
			try
			{
				while (true)
				{
					doctors[super.id].acquire(); // Wait for a patient to enter office
					print("Doctor "+super.id+" listens to symptoms from patient "+currentPatientOfDoctor[super.id]);
					patientDoctorInteraction[currentPatientOfDoctor[super.id]].release(); // Make the patient receive advice
				}
			} catch (InterruptedException e) { }
		}
	}
}